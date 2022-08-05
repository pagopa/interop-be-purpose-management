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
import it.pagopa.interop.purposemanagement.model.decoupling.PurposeUpdate
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.purpose._
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
      Entity(PurposePersistentBehavior.TypeKey)(behaviorFactory(mockDateTimeSupplier))

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

  def updatePurpose(purposeId: UUID, update: PurposeUpdate): PersistentPurpose =
    commander(purposeId).ask(ref => UpdatePurpose(purposeId.toString, update, ref)).futureValue.getValue

  def createVersion(purposeId: UUID, version: PersistentPurposeVersion): PersistentPurposeVersion =
    commander(purposeId).ask(ref => CreatePurposeVersion(purposeId.toString, version, ref)).futureValue.getValue

  //  def createPurpose(purposeId: UUID): Future[Purpose] =
//    for {
//      seed <- Future.successful(
//        PurposeSeed(
//          eserviceId = UUID.randomUUID(),
//          consumerId = UUID.randomUUID(),
//          title = "A title",
//          description = "A description",
//          riskAnalysisForm = Some(riskAnalysisFormSeed)
//        )
//      )
//      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
//      _ = (() => mockUUIDSupplier.get).expects().returning(purposeId).once()
//      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//      _ = (() => mockUUIDSupplier.get).expects().returning(riskAnalysisForm.id).once()
//      purpose <- Unmarshal(makeRequest(data, "purposes", HttpMethods.POST)).to[Purpose]
//    } yield purpose
//
//  def createPurposeVersion(purposeId: UUID, versionId: UUID, seed: PurposeVersionSeed)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] =
//    for {
//      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
//      _ = (() => mockUUIDSupplier.get).expects().returning(versionId).once()
//      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//      purpose <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions", HttpMethods.POST)).to[PurposeVersion]
//    } yield purpose
//
//  def updatePurpose(purposeId: UUID, seed: PurposeUpdateContent)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[Purpose] =
//    for {
//      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
//      _ = (() => mockUUIDSupplier.get).expects().returning(UUID.randomUUID()).once()
//      purpose <- Unmarshal(makeRequest(data, s"purposes/$purposeId", HttpMethods.POST))
//        .to[Purpose]
//    } yield purpose
//
//  def updateDraftPurposeVersion(purposeId: UUID, versionId: UUID, seed: DraftPurposeVersionUpdateContent)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] =
//    for {
//      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
//      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//      purpose <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions/$versionId/update/draft", HttpMethods.POST))
//        .to[PurposeVersion]
//    } yield purpose
//
//  def updateWaitingForApprovalPurposeVersion(
//    purposeId: UUID,
//    versionId: UUID,
//    seed: WaitingForApprovalPurposeVersionUpdateContent
//  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[PurposeVersion] =
//    for {
//      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
//      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//      purpose <- Unmarshal(
//        makeRequest(data, s"purposes/$purposeId/versions/$versionId/update/waitingForApproval", HttpMethods.POST)
//      )
//        .to[PurposeVersion]
//    } yield purpose
//
//  def deletePurpose(
//    purposeId: UUID
//  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Option[String]] =
//    Unmarshal(makeRequest(emptyData, s"purposes/$purposeId", HttpMethods.DELETE)).to[Option[String]]
//
//  def deletePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[Option[String]] =
//    Unmarshal(makeRequest(emptyData, s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)).to[Option[String]]
//
//  def makeFailingRequest[T](url: String, verb: HttpMethod, data: T)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem,
//    marshaller: Marshaller[T, MessageEntity]
//  ): Future[Problem] =
//    for {
//      body    <- Marshal(data).to[MessageEntity].map(_.dataBytes)
//      purpose <- Unmarshal(makeRequest(body, url, verb)).to[Problem]
//    } yield purpose
//
//  def makeFailingRequest(url: String, verb: HttpMethod)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[Problem] = makeFailingRequest(url, verb, "")
//
//  def getPurpose(id: UUID)(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purpose] = {
//    val response = makeRequest(emptyData, s"purposes/${id.toString}", HttpMethods.GET)
//    Unmarshal(response).to[Purpose]
//  }
//
//  def getPurposes(
//    eServiceId: Option[UUID] = None,
//    consumerId: Option[UUID] = None,
//    states: Seq[PurposeVersionState] = Seq.empty
//  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purposes] = {
//    val eServiceParam = eServiceId.fold("")(id => s"eserviceId=${id.toString}")
//    val consumerParam = consumerId.fold("")(id => s"consumerId=${id.toString}")
//    val stateParam    = states.mkString("states=", ",", "")
//
//    val params   = Seq(eServiceParam, consumerParam, stateParam).mkString("?", "&", "")
//    val response = makeRequest(emptyData, s"purposes$params", HttpMethods.GET)
//    Unmarshal(response).to[Purposes]
//  }
//
//  def activateVersion(
//    purposeId: UUID,
//    versionId: UUID,
//    changedBy: ChangedBy,
//    riskAnalysis: Option[PurposeVersionDocument],
//    timestamp: OffsetDateTime = timestamp
//  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[PurposeVersion] = {
//    for {
//      data <- Marshal(
//        ActivatePurposeVersionPayload(
//          riskAnalysis = riskAnalysis,
//          stateChangeDetails = StateChangeDetails(changedBy = changedBy)
//        )
//      )
//        .to[MessageEntity]
//        .map(_.dataBytes)
//      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//      result <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions/$versionId/activate", HttpMethods.POST))
//        .to[PurposeVersion]
//    } yield result
//  }
//
//  def suspendVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] =
//    changeVersionState(purposeId, versionId, changedBy, "suspend")
//
//  def waitForApprovalVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] =
//    changeVersionState(purposeId, versionId, changedBy, "waitForApproval")
//
//  def archiveVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] =
//    changeVersionState(purposeId, versionId, changedBy, "archive")
//
//  def changeVersionState(purposeId: UUID, versionId: UUID, changedBy: ChangedBy, statePath: String)(implicit
//    ec: ExecutionContext,
//    actorSystem: actor.ActorSystem
//  ): Future[PurposeVersion] = for {
//    data <- Marshal(StateChangeDetails(changedBy = changedBy))
//      .to[MessageEntity]
//      .map(_.dataBytes)
//    _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
//    result <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions/$versionId/$statePath", HttpMethods.POST))
//      .to[PurposeVersion]
//  } yield result
//
//  def makeRequest(data: Source[ByteString, Any], path: String, verb: HttpMethod): HttpResponse = {
//    Await.result(
//      Http().singleRequest(
//        HttpRequest(
//          uri = s"$url/$path",
//          method = verb,
//          entity = HttpEntity(ContentTypes.`application/json`, data),
//          headers = requestHeaders
//        )
//      ),
//      Duration.Inf
//    )
//  }
}
