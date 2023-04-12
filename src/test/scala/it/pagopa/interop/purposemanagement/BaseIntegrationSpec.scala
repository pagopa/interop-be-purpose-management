package it.pagopa.interop.purposemanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.interop.purposemanagement.api.PurposeApi
import it.pagopa.interop.purposemanagement.api.impl.{PurposeApiMarshallerImpl, PurposeApiServiceImpl}
import it.pagopa.interop.purposemanagement.model.persistence.PurposePersistentBehavior
import it.pagopa.interop.purposemanagement.server.Controller
import it.pagopa.interop.purposemanagement.server.impl.Main.behaviorFactory
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
abstract class BaseIntegrationSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  private var controller: Option[Controller]                                    = None
  private var bindServer: Option[Future[Http.ServerBinding]]                    = None
  private val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  private val sharding: ClusterSharding = ClusterSharding(system)

  private val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)

  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  override def beforeAll(): Unit = {
    val persistentEntity = Entity(PurposePersistentBehavior.TypeKey)(behaviorFactory())

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val purposeApi = new PurposeApi(
      PurposeApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier, mockDateTimeSupplier),
      PurposeApiMarshallerImpl,
      wrappingDirective
    )

    controller = Some(new Controller(purposeApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def afterAll(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
  }

}
