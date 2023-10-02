package it.pagopa.interop.purposemanagement.model.persistence

import spray.json._
import spray.json.DefaultJsonProtocol._
import it.pagopa.interop.purposemanagement.model.purpose._
import it.pagopa.interop.commons.utils.SprayCommonFormats._

object JsonFormats {

  implicit val ppvsFormat: RootJsonFormat[PersistentPurposeVersionState] =
    new RootJsonFormat[PersistentPurposeVersionState] {
      override def read(json: JsValue): PersistentPurposeVersionState = json match {
        case JsString("Draft")              => Draft
        case JsString("Active")             => Active
        case JsString("Suspended")          => Suspended
        case JsString("Archived")           => Archived
        case JsString("WaitingForApproval") => WaitingForApproval
        case _ => deserializationError("Unable to deserialize json as a PersistentPurposeVersionState")
      }

      override def write(obj: PersistentPurposeVersionState): JsValue = obj match {
        case Archived           => JsString("Archived")
        case Suspended          => JsString("Suspended")
        case Draft              => JsString("Draft")
        case WaitingForApproval => JsString("WaitingForApproval")
        case Active             => JsString("Active")
      }
    }

  implicit val ppvdFormat: RootJsonFormat[PersistentPurposeVersionDocument] = jsonFormat4(
    PersistentPurposeVersionDocument.apply
  )

  implicit val ppvFormat: RootJsonFormat[PersistentPurposeVersion] = jsonFormat9(PersistentPurposeVersion.apply)

  implicit val pramaFormat: RootJsonFormat[PersistentRiskAnalysisMultiAnswer] = jsonFormat3(
    PersistentRiskAnalysisMultiAnswer.apply
  )

  implicit val prasaFormat: RootJsonFormat[PersistentRiskAnalysisSingleAnswer] = jsonFormat3(
    PersistentRiskAnalysisSingleAnswer.apply
  )

  implicit val prafFormat: RootJsonFormat[PersistentRiskAnalysisForm] = jsonFormat5(PersistentRiskAnalysisForm.apply)

  implicit val ppFormat: RootJsonFormat[PersistentPurpose] = jsonFormat13(PersistentPurpose.apply)

  implicit val pcFormat: RootJsonFormat[PurposeCreated]                     = jsonFormat1(PurposeCreated.apply)
  implicit val puFormat: RootJsonFormat[PurposeUpdated]                     = jsonFormat1(PurposeUpdated.apply)
  implicit val pvcFormat: RootJsonFormat[PurposeVersionCreated]             = jsonFormat2(PurposeVersionCreated.apply)
  implicit val pvaFormat: RootJsonFormat[PurposeVersionActivated]           = jsonFormat1(PurposeVersionActivated.apply)
  implicit val pvsFormat: RootJsonFormat[PurposeVersionSuspended]           = jsonFormat1(PurposeVersionSuspended.apply)
  implicit val pvwfaFormat: RootJsonFormat[PurposeVersionWaitedForApproval] = jsonFormat1(
    PurposeVersionWaitedForApproval.apply
  )
  implicit val pvarFormat: RootJsonFormat[PurposeVersionArchived]           = jsonFormat1(PurposeVersionArchived.apply)
  implicit val pvuFormat: RootJsonFormat[PurposeVersionUpdated]             = jsonFormat2(PurposeVersionUpdated.apply)
  implicit val pvdFormat: RootJsonFormat[PurposeVersionDeleted]             = jsonFormat2(PurposeVersionDeleted.apply)
  implicit val pdFormat: RootJsonFormat[PurposeDeleted]                     = jsonFormat1(PurposeDeleted.apply)
}
