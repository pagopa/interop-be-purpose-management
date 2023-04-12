package it.pagopa.interop.purposemanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api._
import it.pagopa.interop.purposemanagement.api.impl._
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.purposemanagement.model.decoupling.{DraftPurposeVersionUpdate, PurposeUpdate}
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.purpose._
import it.pagopa.interop.purposemanagement.model.{PurposeVersionDocument, StateChangeDetails}
import it.pagopa.interop.purposemanagement.server.Controller
import it.pagopa.interop.purposemanagement.server.impl.Dependencies
import org.scalamock.scalatest.MockFactory
import org.scalatest.Assertion
import spray.json._

import java.net.InetAddress
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait ItSpecHelper
    extends ItSpecConfiguration
    with ItCqrsSpec
    with MockFactory
    with SprayJsonSupport
    with DefaultJsonProtocol
    with Dependencies {
  self: ScalaTestWithActorTestKit =>

  val bearerToken: String                   = "token"
  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val apiMarshaller: PurposeApiMarshaller = PurposeApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  val sharding: ClusterSharding                 = ClusterSharding(system)
  def commander(id: UUID): EntityRef[Command]   = commander(id.toString)
  def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(
      PurposePersistentBehavior.TypeKey,
      getShard(id, ApplicationConfiguration.numberOfProjectionTags)
    )

  val httpSystem: ActorSystem[Any]                        =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  val classicSystem: actor.ActorSystem                    = httpSystem.classicSystem

  override def startServer(): Unit = {
    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(PurposePersistentBehavior.TypeKey)(behaviorFactory())

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val attributeApi =
      new PurposeApi(
        PurposeApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier, mockDateTimeSupplier),
        apiMarshaller,
        wrappingDirective
      )

    if (ApplicationConfiguration.projectionsEnabled) initCqrsProjection()

    controller = Some(new Controller(attributeApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()(classicSystem)
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def shutdownServer(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
  }

  def comparePurposes(item1: PersistentPurpose, item2: PersistentPurpose): Assertion =
    sortPurposeArrayFields(item1) shouldBe sortPurposeArrayFields(item2)

  def sortPurposeArrayFields(purpose: PersistentPurpose): PersistentPurpose =
    purpose.copy(versions = purpose.versions.sortBy(_.id))

  def createPurpose(persistentPurpose: PersistentPurpose): PersistentPurpose =
    commander(persistentPurpose.id).ask(ref => CreatePurpose(persistentPurpose, ref)).futureValue.getValue

  def deletePurpose(purposeId: UUID): Unit =
    commander(purposeId).ask(ref => DeletePurpose(purposeId.toString, ref)).futureValue.getValue

  def updatePurpose(purposeId: UUID, update: PurposeUpdate): PersistentPurpose =
    commander(purposeId).ask(ref => UpdatePurpose(purposeId.toString, update, ref)).futureValue.getValue

  def createVersion(purposeId: UUID, version: PersistentPurposeVersion): PersistentPurposeVersion =
    commander(purposeId).ask(ref => CreatePurposeVersion(purposeId.toString, version, ref)).futureValue.getValue

  def deleteVersion(purposeId: UUID, versionId: UUID): Unit =
    commander(purposeId)
      .ask(ref => DeletePurposeVersion(purposeId.toString, versionId.toString, ref))
      .futureValue
      .getValue

  def activateVersion(
    purposeId: UUID,
    versionId: UUID,
    riskAnalysis: Option[PurposeVersionDocument],
    stateChangeDetails: StateChangeDetails
  ): PersistentPurpose = {
    (() => mockDateTimeSupplier.get()).expects().returning(ItSpecData.timestamp).once()
    commander(purposeId)
      .ask(ref => ActivatePurposeVersion(purposeId.toString, versionId.toString, riskAnalysis, stateChangeDetails, ref))
      .futureValue
      .getValue
  }

  def suspendVersion(purposeId: UUID, versionId: UUID, stateChangeDetails: StateChangeDetails): PersistentPurpose = {
    (() => mockDateTimeSupplier.get()).expects().returning(ItSpecData.timestamp).once()
    commander(purposeId)
      .ask(ref => SuspendPurposeVersion(purposeId.toString, versionId.toString, stateChangeDetails, ref))
      .futureValue
      .getValue
  }

  def waitForApprovalVersion(
    purposeId: UUID,
    versionId: UUID,
    stateChangeDetails: StateChangeDetails
  ): PersistentPurpose = {
    (() => mockDateTimeSupplier.get()).expects().returning(ItSpecData.timestamp).once()
    commander(purposeId)
      .ask(ref => WaitForApprovalPurposeVersion(purposeId.toString, versionId.toString, stateChangeDetails, ref))
      .futureValue
      .getValue
  }

  def archiveVersion(purposeId: UUID, versionId: UUID, stateChangeDetails: StateChangeDetails): PersistentPurpose = {
    (() => mockDateTimeSupplier.get()).expects().returning(ItSpecData.timestamp).once()
    commander(purposeId)
      .ask(ref => ArchivePurposeVersion(purposeId.toString, versionId.toString, stateChangeDetails, ref))
      .futureValue
      .getValue
  }

  def updateDraftVersion(
    purposeId: UUID,
    versionId: UUID,
    update: DraftPurposeVersionUpdate
  ): PersistentPurposeVersion =
    commander(purposeId)
      .ask(ref => UpdateDraftPurposeVersion(purposeId.toString, versionId.toString, update, ref))
      .futureValue
      .getValue

}
