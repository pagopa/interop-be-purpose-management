package it.pagopa.interop.purposemanagement.server.impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.PurposeApi
import it.pagopa.interop.purposemanagement.api.impl.{PurposeApiMarshallerImpl, PurposeApiServiceImpl, problemOf}
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.purposemanagement.model.persistence.PurposeEventsSerde
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag,
  projectionsEnabled
}
import it.pagopa.interop.purposemanagement.model.persistence.{
  Command,
  PurposePersistentBehavior,
  PurposePersistentProjection
}
import it.pagopa.interop.purposemanagement.server.Controller
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.util.Try
import scala.concurrent.ExecutionContext
import it.pagopa.interop.commons.queue.QueueWriter
import com.atlassian.oai.validator.report.ValidationReport
import akka.http.scaladsl.server.Route
import scala.concurrent.Future
import it.pagopa.interop.commons.utils.TypeConversions._

trait Dependencies {

  System.setProperty("kanela.show-banner", "false")

  val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

  def behaviorFactory(offsetDateTimeSupplier: OffsetDateTimeSupplier): EntityContext[Command] => Behavior[Command] =
    entityContext =>
      PurposePersistentBehavior(
        entityContext.shard,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        offsetDateTimeSupplier,
        projectionTag(math.abs(entityContext.entityId.hashCode % numberOfProjectionTags))
      )

  val purposePersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(PurposePersistentBehavior.TypeKey)(behaviorFactory(dateTimeSupplier))

  def initProjections()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) = {
    val queueWriter: QueueWriter =
      QueueWriter.get(ApplicationConfiguration.queueUrl)(PurposeEventsSerde.purposeToJson)

    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val purposePersistentProjection = new PurposePersistentProjection(dbConfig, queueWriter)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = "purpose-projections",
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(purposePersistentProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def getJwtValidator()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(StatusCodes.BadRequest, ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report)))
    complete(error.status, error)(PurposeApiMarshallerImpl.toEntityMarshallerProblem)
  }

  def purposeApi(sharding: ClusterSharding, jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_]) =
    new PurposeApi(
      PurposeApiServiceImpl(actorSystem, sharding, purposePersistenceEntity, uuidSupplier, dateTimeSupplier),
      PurposeApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

}
