package it.pagopa.interop.purposemanagement.model.persistence

import spray.json._
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import JsonFormats._

object PurposeEventsSerde {

  val projectablePurposeToJson: PartialFunction[ProjectableEvent, JsValue] = { case event: Event =>
    purposeToJson(event)
  }

  def purposeToJson(event: Event): JsValue = event match {
    case x: PurposeCreated                  => x.toJson
    case x: PurposeUpdated                  => x.toJson
    case x: PurposeVersionCreated           => x.toJson
    case x: PurposeVersionActivated         => x.toJson
    case x: PurposeVersionSuspended         => x.toJson
    case x: PurposeVersionWaitedForApproval => x.toJson
    case x: PurposeVersionArchived          => x.toJson
    case x: PurposeVersionUpdated           => x.toJson
    case x: PurposeVersionDeleted           => x.toJson
    case x: PurposeDeleted                  => x.toJson
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

}
