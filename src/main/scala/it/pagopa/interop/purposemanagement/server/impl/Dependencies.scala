package it.pagopa.interop.purposemanagement.server.impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{Problem => CommonProblem}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.PurposeApi
import it.pagopa.interop.purposemanagement.api.impl.ResponseHandlers.serviceCode
import it.pagopa.interop.purposemanagement.api.impl.{PurposeApiMarshallerImpl, PurposeApiServiceImpl}
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.purposemanagement.model.persistence.projection.{
  PurposeCqrsProjection,
  PurposeNotificationProjection
}
import it.pagopa.interop.purposemanagement.model.persistence.{Command, PurposeEventsSerde, PurposePersistentBehavior}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

trait Dependencies {

  implicit val loggerTI: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog]("OAuth2JWTValidatorAsContexts")

  val uuidSupplier: UUIDSupplier               = UUIDSupplier
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplier

  def behaviorFactory(): EntityContext[Command] => Behavior[Command] =
    entityContext =>
      PurposePersistentBehavior(
        entityContext.shard,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        projectionTag(math.abs(entityContext.entityId.hashCode % numberOfProjectionTags))
      )

  val purposePersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(PurposePersistentBehavior.TypeKey)(behaviorFactory())

  def initProjections(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    initNotificationProjection(blockingEc)
    initCqrsProjection()
  }

  def initNotificationProjection(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val queueWriter: QueueWriter =
      QueueWriter.get(ApplicationConfiguration.queueUrl)(PurposeEventsSerde.projectablePurposeToJson)(blockingEc)

    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val notificationProjectionId = "purpose-notification-projections"

    val purposeNotificationProjection = PurposeNotificationProjection(dbConfig, queueWriter, notificationProjectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = notificationProjectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(purposeNotificationProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def initCqrsProjection()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val mongoDbConfig = ApplicationConfiguration.mongoDb

    val cqrsProjectionId = "purpose-cqrs-projections"

    val purposeCqrsProjection = PurposeCqrsProjection.projection(dbConfig, mongoDbConfig, cqrsProjectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = cqrsProjectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(purposeCqrsProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      CommonProblem(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report), serviceCode, None)
    complete(error.status, error)
  }

  def purposeApi(sharding: ClusterSharding, jwtReader: JWTReader)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ) =
    new PurposeApi(
      PurposeApiServiceImpl(actorSystem, sharding, purposePersistenceEntity, uuidSupplier, dateTimeSupplier),
      PurposeApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

}
