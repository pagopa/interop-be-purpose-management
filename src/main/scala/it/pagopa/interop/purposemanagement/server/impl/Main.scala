package it.pagopa.interop.purposemanagement.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
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

object Main extends App {

  val dependenciesLoaded: Try[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey] = keyset

      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  val jwtValidator =
    dependenciesLoaded.get //THIS IS THE END OF THE WORLD. Exceptions are welcomed here.

  Kamon.init()

  def buildPersistentEntity(dateTimeSupplier: OffsetDateTimeSupplier): Entity[Command, ShardingEnvelope[Command]] =
    Entity(typeKey = PurposePersistentBehavior.TypeKey) { entityContext =>
      PurposePersistentBehavior(
        entityContext.shard,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        dateTimeSupplier
      )
    }

  locally {
    val _ = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        import akka.actor.typed.scaladsl.adapter._
        implicit val classicSystem: classic.ActorSystem = context.system.toClassic

        val cluster = Cluster(context.system)

        context.log.info(
          "Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ", build info = " + buildinfo.BuildInfo.toString + ")"
        )

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
        val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

        val purposePersistenceEntity: Entity[Command, ShardingEnvelope[Command]] =
          buildPersistentEntity(dateTimeSupplier)

        val _ = sharding.init(purposePersistenceEntity)

        val settings: ClusterShardingSettings = purposePersistenceEntity.settings match {
          case None    => ClusterShardingSettings(context.system)
          case Some(s) => s
        }
        val persistence =
          classicSystem.classicSystem.settings.config.getString("purpose-management.persistence")
        if (persistence == "jdbc-journal") {
          val dbConfig: DatabaseConfig[JdbcProfile] =
            DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

          val purposePersistentProjection =
            new PurposePersistentProjection(context.system, purposePersistenceEntity, dbConfig)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "purpose-projections",
            numberOfInstances = settings.numberOfShards,
            behaviorFactory = (i: Int) => ProjectionBehavior(purposePersistentProjection.projections(i)),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val purposeApi = new PurposeApi(
          PurposeApiServiceImpl(context.system, sharding, purposePersistenceEntity, uuidSupplier, dateTimeSupplier),
          PurposeApiMarshallerImpl,
          jwtValidator.OAuth2JWTValidatorAsContexts
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          purposeApi,
          validationExceptionToRoute = Some(report => {
            val error =
              problemOf(
                StatusCodes.BadRequest,
                ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
              )
            complete(error.status, error)(PurposeApiMarshallerImpl.toEntityMarshallerProblem)
          })
        )

        val _ = Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.info("MemberEvent: {}", event)
            Behaviors.same
          }),
          "listener"
        )

        Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

        val _ = AkkaManagement(classicSystem).start()
        ClusterBootstrap.get(classicSystem).start()
        Behaviors.empty
      },
      "interop-be-purpose-management"
    )
  }

}
