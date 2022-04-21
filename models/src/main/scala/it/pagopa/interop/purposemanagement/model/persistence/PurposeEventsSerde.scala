package it.pagopa.interop.purposemanagement.model.persistence

import spray.json._
import spray.json.DefaultJsonProtocol._
import it.pagopa.interop.purposemanagement.model.purpose._
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import it.pagopa.interop.commons.queue.message.ProjectableEvent

object PurposeEventsSerde {

  val purposeToJson: PartialFunction[ProjectableEvent, JsValue] = {
    case x @ PurposeCreated(_)                  => x.toJson
    case x @ PurposeUpdated(_)                  => x.toJson
    case x @ PurposeVersionCreated(_, _)        => x.toJson
    case x @ PurposeVersionActivated(_)         => x.toJson
    case x @ PurposeVersionSuspended(_)         => x.toJson
    case x @ PurposeVersionWaitedForApproval(_) => x.toJson
    case x @ PurposeVersionArchived(_)          => x.toJson
    case x @ PurposeVersionUpdated(_, _)        => x.toJson
    case x @ PurposeVersionDeleted(_, _)        => x.toJson
    case x @ PurposeDeleted(_)                  => x.toJson
  }

  val jsonToPurpose: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `purposeCreated`                  => _.convertTo[PurposeCreated]
    case `purposeUpdated`                  => _.convertTo[PurposeUpdated]
    case `purposeVersionCreated`           => _.convertTo[PurposeVersionCreated]
    case `purposeVersionActivated`         => _.convertTo[PurposeVersionActivated]
    case `purposeVersionSuspended`         => _.convertTo[PurposeVersionSuspended]
    case `purposeVersionWaitedForApproval` => _.convertTo[PurposeVersionWaitedForApproval]
    case `purposeVersionArchived`          => _.convertTo[PurposeVersionArchived]
    case `purposeVersionUpdated`           => _.convertTo[PurposeVersionUpdated]
    case `purposeVersionDeleted`           => _.convertTo[PurposeVersionDeleted]
    case `purposeDeleted`                  => _.convertTo[PurposeDeleted]
  }

  def getKind(e: Event): String = e match {
    case PurposeCreated(_)                  => purposeCreated
    case PurposeUpdated(_)                  => purposeUpdated
    case PurposeVersionCreated(_, _)        => purposeVersionCreated
    case PurposeVersionActivated(_)         => purposeVersionActivated
    case PurposeVersionSuspended(_)         => purposeVersionSuspended
    case PurposeVersionWaitedForApproval(_) => purposeVersionWaitedForApproval
    case PurposeVersionArchived(_)          => purposeVersionArchived
    case PurposeVersionUpdated(_, _)        => purposeVersionUpdated
    case PurposeVersionDeleted(_, _)        => purposeVersionDeleted
    case PurposeDeleted(_)                  => purposeDeleted
  }

  private val purposeCreated: String                  = "purpose_created"
  private val purposeUpdated: String                  = "purpose_updated"
  private val purposeVersionCreated: String           = "purpose_version_created"
  private val purposeVersionActivated: String         = "purpose_version_activated"
  private val purposeVersionSuspended: String         = "purpose_version_suspended"
  private val purposeVersionWaitedForApproval: String = "purpose_version_waited_for_approval"
  private val purposeVersionArchived: String          = "purpose_version_archived"
  private val purposeVersionUpdated: String           = "purpose_version_updated"
  private val purposeVersionDeleted: String           = "purpose_version_deleted"
  private val purposeDeleted: String                  = "purpose_deleted"

  private implicit val ppvsFormat: RootJsonFormat[PersistentPurposeVersionState] =
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

  private implicit val ppvdFormat: RootJsonFormat[PersistentPurposeVersionDocument] = jsonFormat4(
    PersistentPurposeVersionDocument.apply
  )

  private implicit val ppvFormat: RootJsonFormat[PersistentPurposeVersion] = jsonFormat8(PersistentPurposeVersion.apply)

  private implicit val pramaFormat: RootJsonFormat[PersistentRiskAnalysisMultiAnswer] = jsonFormat3(
    PersistentRiskAnalysisMultiAnswer.apply
  )

  private implicit val prasaFormat: RootJsonFormat[PersistentRiskAnalysisSingleAnswer] = jsonFormat3(
    PersistentRiskAnalysisSingleAnswer.apply
  )

  private implicit val prafFormat: RootJsonFormat[PersistentRiskAnalysisForm] = jsonFormat4(
    PersistentRiskAnalysisForm.apply
  )

  private implicit val ppFormat: RootJsonFormat[PersistentPurpose] = jsonFormat11(PersistentPurpose.apply)

  private implicit val pcFormat: RootJsonFormat[PurposeCreated]           = jsonFormat1(PurposeCreated.apply)
  private implicit val puFormat: RootJsonFormat[PurposeUpdated]           = jsonFormat1(PurposeUpdated.apply)
  private implicit val pvcFormat: RootJsonFormat[PurposeVersionCreated]   = jsonFormat2(PurposeVersionCreated.apply)
  private implicit val pvaFormat: RootJsonFormat[PurposeVersionActivated] = jsonFormat1(PurposeVersionActivated.apply)
  private implicit val pvsFormat: RootJsonFormat[PurposeVersionSuspended] = jsonFormat1(PurposeVersionSuspended.apply)
  private implicit val pvwfaFormat: RootJsonFormat[PurposeVersionWaitedForApproval] = jsonFormat1(
    PurposeVersionWaitedForApproval.apply
  )
  private implicit val pvarFormat: RootJsonFormat[PurposeVersionArchived] = jsonFormat1(PurposeVersionArchived.apply)
  private implicit val pvuFormat: RootJsonFormat[PurposeVersionUpdated]   = jsonFormat2(PurposeVersionUpdated.apply)
  private implicit val pvdFormat: RootJsonFormat[PurposeVersionDeleted]   = jsonFormat2(PurposeVersionDeleted.apply)
  private implicit val pdFormat: RootJsonFormat[PurposeDeleted]           = jsonFormat1(PurposeDeleted.apply)
}
