package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

sealed trait PersistentPurposeState {
  def toApi: PurposeState = this match {
    case PersistentPurposeState.Draft              => PurposeState.DRAFT
    case PersistentPurposeState.Active             => PurposeState.ACTIVE
    case PersistentPurposeState.Suspended          => PurposeState.SUSPENDED
    case PersistentPurposeState.Archived           => PurposeState.ARCHIVED
    case PersistentPurposeState.WaitingForApproval => PurposeState.WAITING_FOR_APPROVAL
  }
}

object PersistentPurposeState {
  case object Draft              extends PersistentPurposeState
  case object Active             extends PersistentPurposeState
  case object Suspended          extends PersistentPurposeState
  case object Archived           extends PersistentPurposeState
  case object WaitingForApproval extends PersistentPurposeState

  def fromApi(status: PurposeState): PersistentPurposeState = status match {
    case PurposeState.DRAFT                => Draft
    case PurposeState.ACTIVE               => Active
    case PurposeState.SUSPENDED            => Suspended
    case PurposeState.ARCHIVED             => Archived
    case PurposeState.WAITING_FOR_APPROVAL => PersistentPurposeState.WaitingForApproval
  }
}
