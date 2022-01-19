package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

sealed trait PersistentPurposeVersionState {
  def toApi: PurposeVersionState = this match {
    case PersistentPurposeVersionState.Draft              => PurposeVersionState.DRAFT
    case PersistentPurposeVersionState.Active             => PurposeVersionState.ACTIVE
    case PersistentPurposeVersionState.Suspended          => PurposeVersionState.SUSPENDED
    case PersistentPurposeVersionState.Archived           => PurposeVersionState.ARCHIVED
    case PersistentPurposeVersionState.WaitingForApproval => PurposeVersionState.WAITING_FOR_APPROVAL
  }
}

object PersistentPurposeVersionState {
  case object Draft              extends PersistentPurposeVersionState
  case object Active             extends PersistentPurposeVersionState
  case object Suspended          extends PersistentPurposeVersionState
  case object Archived           extends PersistentPurposeVersionState
  case object WaitingForApproval extends PersistentPurposeVersionState

  def fromApi(status: PurposeVersionState): PersistentPurposeVersionState = status match {
    case PurposeVersionState.DRAFT                => Draft
    case PurposeVersionState.ACTIVE               => Active
    case PurposeVersionState.SUSPENDED            => Suspended
    case PurposeVersionState.ARCHIVED             => Archived
    case PurposeVersionState.WAITING_FOR_APPROVAL => PersistentPurposeVersionState.WaitingForApproval
  }
}
