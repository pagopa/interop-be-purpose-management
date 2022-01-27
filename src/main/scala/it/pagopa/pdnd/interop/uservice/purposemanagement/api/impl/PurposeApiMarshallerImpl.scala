package it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.PurposeApiMarshaller
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import spray.json._

object PurposeApiMarshallerImpl extends PurposeApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerPurposeSeed: FromEntityUnmarshaller[PurposeSeed] =
    sprayJsonUnmarshaller[PurposeSeed]

  override implicit def toEntityMarshallerPurposearray: ToEntityMarshaller[Seq[Purpose]] =
    sprayJsonMarshaller[Seq[Purpose]]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]

  override implicit def fromEntityUnmarshallerStateChangeDetails: FromEntityUnmarshaller[StateChangeDetails] =
    sprayJsonUnmarshaller[StateChangeDetails]

  override implicit def fromEntityUnmarshallerPurposeVersionSeed: FromEntityUnmarshaller[PurposeVersionSeed] =
    sprayJsonUnmarshaller[PurposeVersionSeed]

  override implicit def toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion] =
    sprayJsonMarshaller[PurposeVersion]

  override implicit def fromEntityUnmarshallerPurposeVersionUpdateContent
    : FromEntityUnmarshaller[PurposeVersionUpdateContent] = sprayJsonUnmarshaller[PurposeVersionUpdateContent]

  override implicit def fromEntityUnmarshallerActivatePurposeVersionPayload
    : FromEntityUnmarshaller[ActivatePurposeVersionPayload] = sprayJsonUnmarshaller[ActivatePurposeVersionPayload]
}
