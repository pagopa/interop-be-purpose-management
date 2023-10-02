package it.pagopa.interop

import akka.NotUsed
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.impl._
import it.pagopa.interop.purposemanagement.model._
import it.pagopa.interop.purposemanagement.model.purpose.PersistentRiskAnalysisForm
import org.scalamock.scalatest.MockFactory

import java.net.InetAddress
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

package object purposemanagement extends MockFactory {

  final lazy val url: String                =
    s"http://localhost:18088/purpose-management/${buildinfo.BuildInfo.interfaceVersion}"
  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  final val riskAnalysisId             = UUID.randomUUID()
  final val timestamp                  = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 44, ZoneOffset.UTC)
  final val riskAnalysisFormSeed       = RiskAnalysisFormSeed(
    riskAnalysisId = Some(riskAnalysisId),
    version = "1.0",
    singleAnswers = Seq.empty,
    multiAnswers = Seq.empty
  )
  final val riskAnalysisForm           =
    RiskAnalysisForm(
      id = UUID.randomUUID(),
      riskAnalysisId = Some(riskAnalysisId),
      version = "1.0",
      singleAnswers = Seq.empty,
      multiAnswers = Seq.empty
    )
  final val persistentRiskAnalysisForm =
    PersistentRiskAnalysisForm(
      id = riskAnalysisForm.id,
      riskAnalysisId = Some(riskAnalysisId),
      version = riskAnalysisForm.version,
      singleAnswers = Seq.empty,
      multiAnswers = Seq.empty
    )

  val mockUUIDSupplier: UUIDSupplier               = mock[UUIDSupplier]
  val mockDateTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]

  val emptyData: Source[ByteString, NotUsed] = Source.empty[ByteString]

  implicit def toEntityMarshallerPurposeSeed: ToEntityMarshaller[PurposeSeed] =
    sprayJsonMarshaller[PurposeSeed]

  implicit def toEntityMarshallerPurposeVersionSeed: ToEntityMarshaller[PurposeVersionSeed] =
    sprayJsonMarshaller[PurposeVersionSeed]

  implicit def toEntityMarshallerStateChangeDetailsSeed: ToEntityMarshaller[StateChangeDetails] =
    sprayJsonMarshaller[StateChangeDetails]

  implicit def toEntityMarshallerActivateVersionSeed: ToEntityMarshaller[ActivatePurposeVersionPayload] =
    sprayJsonMarshaller[ActivatePurposeVersionPayload]

  implicit def fromEntityUnmarshallerWaitingForApprovalPurposeVersionUpdateContent
    : FromEntityUnmarshaller[WaitingForApprovalPurposeVersionUpdateContent] =
    sprayJsonUnmarshaller[WaitingForApprovalPurposeVersionUpdateContent]

  implicit def toEntityMarshallerWaitingForApprovalPurposeVersionUpdateContent
    : ToEntityMarshaller[WaitingForApprovalPurposeVersionUpdateContent] =
    sprayJsonMarshaller[WaitingForApprovalPurposeVersionUpdateContent]

  implicit def toEntityMarshallerPurposeUpdateContent: ToEntityMarshaller[PurposeUpdateContent] =
    sprayJsonMarshaller[PurposeUpdateContent]

  implicit def fromEntityUnmarshallerPurposes: FromEntityUnmarshaller[Purposes] =
    sprayJsonUnmarshaller[Purposes]

  implicit def fromEntityUnmarshallerPurpose: FromEntityUnmarshaller[Purpose] =
    sprayJsonUnmarshaller[Purpose]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  implicit def fromEntityUnmarshallerPurposeVersion: FromEntityUnmarshaller[PurposeVersion] =
    sprayJsonUnmarshaller[PurposeVersion]

}
