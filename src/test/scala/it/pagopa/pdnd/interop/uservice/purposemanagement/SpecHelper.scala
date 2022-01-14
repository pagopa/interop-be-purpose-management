package it.pagopa.pdnd.interop.uservice.purposemanagement

import akka.actor
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model.{HttpMethods, MessageEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl.serviceErrorCodePrefix
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

  def makeFailingRequest[T](url: String, data: T)(implicit
    ec: ExecutionContext,
    actorSystem: actor.ActorSystem,
    marshaller: Marshaller[T, MessageEntity]
  ): Future[Problem] =
    for {
      body    <- Marshal(data).to[MessageEntity].map(_.dataBytes)
      purpose <- Unmarshal(makeRequest(body, url, HttpMethods.POST)).to[Problem]
    } yield purpose

  def problemErrorFromError(error: ComponentError): ProblemError =
    ProblemError(code = s"$serviceErrorCodePrefix-${error.code}", detail = error.msg)

//  def getPurpose(id: String)(implicit ec: ExecutionContext, actorSystem: actor.ActorSystem): Future[Purpose] = {
//
//    val response = makeRequest(emptyData, s"purpose/$id", HttpMethods.GET)
//    val result = for {
//      purpose <- Unmarshal(response).to[Purpose]
//    } yield purpose
//    result
//  }
//
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
