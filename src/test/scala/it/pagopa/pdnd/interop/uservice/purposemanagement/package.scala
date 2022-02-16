package it.pagopa.pdnd.interop.uservice

import akka.NotUsed
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentRiskAnalysisForm
import org.scalamock.scalatest.MockFactory

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

package object purposemanagement extends MockFactory {

  final lazy val url: String =
    s"http://localhost:18088/pdnd-interop-uservice-purpose-management/${buildinfo.BuildInfo.interfaceVersion}"
  final val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken("token")))

  final val timestamp            = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 44, ZoneOffset.UTC)
  final val riskAnalysisFormSeed = RiskAnalysisFormSeed(version = "1.0", Seq.empty, Seq.empty)
  final val riskAnalysisForm     = RiskAnalysisForm(id = UUID.randomUUID(), version = "1.0", Seq.empty, Seq.empty)
  final val persistentRiskAnalysisForm =
    PersistentRiskAnalysisForm(id = riskAnalysisForm.id, version = riskAnalysisForm.version, Seq.empty, Seq.empty)

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

  implicit def toEntityMarshallerPurposeVersionUpdateContent: ToEntityMarshaller[PurposeVersionUpdateContent] =
    sprayJsonMarshaller[PurposeVersionUpdateContent]

  implicit def toEntityMarshallerPurposeUpdateContent: ToEntityMarshaller[PurposeUpdatePayload] =
    sprayJsonMarshaller[PurposeUpdatePayload]

  implicit def fromEntityUnmarshallerPurposes: FromEntityUnmarshaller[Purposes] =
    sprayJsonUnmarshaller[Purposes]

  implicit def fromEntityUnmarshallerPurpose: FromEntityUnmarshaller[Purpose] =
    sprayJsonUnmarshaller[Purpose]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  implicit def fromEntityUnmarshallerPurposeVersion: FromEntityUnmarshaller[PurposeVersion] =
    sprayJsonUnmarshaller[PurposeVersion]

}
