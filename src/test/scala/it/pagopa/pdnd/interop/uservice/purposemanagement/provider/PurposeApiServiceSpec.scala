package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.Authenticator
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.PurposeApi
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl.{PurposeApiMarshallerImpl, PurposeApiServiceImpl}
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.PurposeManagementErrors.CreatePurposeVersionBadRequest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.purposemanagement.server.impl.Main
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class PurposeApiServiceSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

  val sharding: ClusterSharding = ClusterSharding(system)

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)

  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  override def beforeAll(): Unit = {
    val persistentEntity = Main.buildPersistentEntity()

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val purposeApi = new PurposeApi(
      new PurposeApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier, mockDateTimeSupplier),
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
    println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
    println("Resources cleaned")
  }

  "Creation of a new purpose" must {

    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)

      val response: Future[Purpose] = createPurpose(purposeId, purposeSeed)

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        createdAt = timestamp,
        updatedAt = None
      )

      response.futureValue shouldBe expected
    }

    "Creation of a new purpose version" must {

      "succeed if purpose exists" in {
        val purposeId  = UUID.randomUUID()
        val versionId  = UUID.randomUUID()
        val eServiceId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()

        val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)

        val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

        val response: Future[PurposeVersion] =
          for {
            _      <- createPurpose(purposeId, purposeSeed)
            result <- createPurposeVersion(purposeId, versionId, versionSeed)
          } yield result

        val expected =
          PurposeVersion(id = versionId, state = versionSeed.state, createdAt = timestamp, expectedApprovalDate = None)

        response.futureValue shouldBe expected
      }

      "fail if purpose does not exist" in {
        val purposeId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

        (() => mockUUIDSupplier.get).expects().returning(versionId).once()
        (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

        val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId/versions", versionSeed)

        val result = response.futureValue
        result.status shouldBe 400
        result.errors shouldBe Seq(problemErrorFromError(CreatePurposeVersionBadRequest))

      }
    }
  }

}
