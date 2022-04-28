package it.pagopa.interop.purposemanagement.model.purpose

object PersistentPurposeVersionState

sealed trait PersistentPurposeVersionState

case object Draft              extends PersistentPurposeVersionState
case object Active             extends PersistentPurposeVersionState
case object Suspended          extends PersistentPurposeVersionState
case object Archived           extends PersistentPurposeVersionState
case object WaitingForApproval extends PersistentPurposeVersionState
