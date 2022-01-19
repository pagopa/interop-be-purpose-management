package it.pagopa.pdnd.interop.uservice.purposemanagement

import akka.actor
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait SpecHelper {

  def createPurpose(purposeId: UUID, seed: PurposeSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Purpose] =
    for {
      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
      _ = (() => mockUUIDSupplier.get).expects().returning(purposeId).once()
      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
      purpose <- Unmarshal(makeRequest(data, "purposes", HttpMethods.POST)).to[Purpose]
    } yield purpose

  def createPurposeVersion(purposeId: UUID, versionId: UUID, seed: PurposeVersionSeed)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[PurposeVersion] =
    for {
      data <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
      creationsCount = if (seed.riskAnalysis.isDefined) 2 else 1
      _              = (() => mockUUIDSupplier.get).expects().returning(versionId).repeated(creationsCount)
      _              = (() => mockDateTimeSupplier.get).expects().returning(timestamp).repeated(creationsCount)
      purpose <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions", HttpMethods.POST)).to[PurposeVersion]
    } yield purpose

  def makeFailingRequest[T](url: String, verb: HttpMethod, data: T)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem,
    marshaller: Marshaller[T, MessageEntity]
  ): Future[Problem] =
    for {
      body    <- Marshal(data).to[MessageEntity].map(_.dataBytes)
      purpose <- Unmarshal(makeRequest(body, url, verb)).to[Problem]
    } yield purpose

  def makeFailingRequest(url: String, verb: HttpMethod)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Problem] = makeFailingRequest(url, verb, "")

  def getPurpose(id: UUID)(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purpose] = {
    val response = makeRequest(emptyData, s"purposes/${id.toString}", HttpMethods.GET)
    Unmarshal(response).to[Purpose]
  }

  def getPurposes(
    eServiceId: Option[UUID] = None,
    consumerId: Option[UUID] = None,
    states: Seq[PurposeVersionState] = Seq.empty
  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Seq[Purpose]] = {
    val eServiceParam = eServiceId.fold("")(id => s"eserviceId=${id.toString}")
    val consumerParam = consumerId.fold("")(id => s"consumerId=${id.toString}")
    val stateParam    = states.mkString("states=", ",", "")

    val params   = Seq(eServiceParam, consumerParam, stateParam).mkString("?", "&", "")
    val response = makeRequest(emptyData, s"purposes$params", HttpMethods.GET)
    Unmarshal(response).to[Seq[Purpose]]
  }

  def activateVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Option[String]] =
    changeVersionState(purposeId, versionId, changedBy, "activate")

  def suspendVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Option[String]] =
    changeVersionState(purposeId, versionId, changedBy, "suspend")

  def archiveVersion(purposeId: UUID, versionId: UUID, changedBy: ChangedBy)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Option[String]] =
    changeVersionState(purposeId, versionId, changedBy, "archive")

  def changeVersionState(purposeId: UUID, versionId: UUID, changedBy: ChangedBy, statePath: String)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem
  ): Future[Option[String]] = for {
    data <- Marshal(StateChangeDetails(changedBy = Some(changedBy)))
      .to[MessageEntity]
      .map(_.dataBytes)
    result <- Unmarshal(makeRequest(data, s"purposes/$purposeId/versions/$versionId/$statePath", HttpMethods.POST))
      .to[Option[String]]
  } yield result

  def makeRequest(data: Source[ByteString, Any], path: String, verb: HttpMethod)(implicit
    actorSystem: ActorSystem
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"$url/$path",
          method = verb,
          entity = HttpEntity(ContentTypes.`application/json`, data),
          headers = authorization
        )
      ),
      Duration.Inf
    )
  }
}
