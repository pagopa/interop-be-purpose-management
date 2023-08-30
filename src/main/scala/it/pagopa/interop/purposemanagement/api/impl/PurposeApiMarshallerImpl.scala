package it.pagopa.interop.purposemanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.purposemanagement.api.PurposeApiMarshaller
import it.pagopa.interop.purposemanagement.model._
import spray.json._

object PurposeApiMarshallerImpl extends PurposeApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerPurposeSeed: FromEntityUnmarshaller[PurposeSeed] =
    sprayJsonUnmarshaller[PurposeSeed]

  override implicit def toEntityMarshallerPurposes: ToEntityMarshaller[Purposes] =
    sprayJsonMarshaller[Purposes]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]

  override implicit def fromEntityUnmarshallerStateChangeDetails: FromEntityUnmarshaller[StateChangeDetails] =
    sprayJsonUnmarshaller[StateChangeDetails]

  override implicit def fromEntityUnmarshallerPurposeVersionSeed: FromEntityUnmarshaller[PurposeVersionSeed] =
    sprayJsonUnmarshaller[PurposeVersionSeed]

  override implicit def toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion] =
    sprayJsonMarshaller[PurposeVersion]

  override implicit def fromEntityUnmarshallerWaitingForApprovalPurposeVersionUpdateContent
    : FromEntityUnmarshaller[WaitingForApprovalPurposeVersionUpdateContent] =
    sprayJsonUnmarshaller[WaitingForApprovalPurposeVersionUpdateContent]

  override implicit def fromEntityUnmarshallerActivatePurposeVersionPayload
    : FromEntityUnmarshaller[ActivatePurposeVersionPayload] = sprayJsonUnmarshaller[ActivatePurposeVersionPayload]

  override implicit def fromEntityUnmarshallerPurposeUpdateContent: FromEntityUnmarshaller[PurposeUpdateContent] =
    sprayJsonUnmarshaller[PurposeUpdateContent]
}
