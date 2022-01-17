package it.pagopa.pdnd.interop.uservice.purposemanagement

import akka.actor
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, MessageEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

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
      _ = (() => mockUUIDSupplier.get).expects().returning(versionId).once()
      _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
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
    val stateParam    = states.fold("states=")((a, s) => s"$a,${s.toString}")

    val params   = Seq(eServiceParam, consumerParam, stateParam).mkString("?", "&", "")
    val response = makeRequest(emptyData, s"purposes$params", HttpMethods.GET)
    Unmarshal(response).to[Seq[Purpose]]
  }

//  def activatePurpose(
//    purpose: Purpose
//  )(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purpose] = for {
//    data <- Marshal(StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER)))
//      .to[MessageEntity]
//      .map(_.dataBytes)
//    activated <- Unmarshal(makeRequest(data, s"purposes/${purpose.id.toString}/activate", HttpMethods.POST))
//      .to[Purpose]
//  } yield activated
//
//  def suspendPurpose(purpose: Purpose)(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purpose] =
//    for {
//      data <- Marshal(StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER)))
//        .to[MessageEntity]
//        .map(_.dataBytes)
//      suspended <- Unmarshal(makeRequest(data, s"purposes/${purpose.id.toString}/suspend", HttpMethods.POST))
//        .to[Purpose]
//    } yield suspended

}
