package it.pagopa.interop.purposemanagement.error

import it.pagopa.interop.purposemanagement.model.purpose.PersistentPurposeVersionState

object InternalErrors {
  final case class PurposeNotFound(purposeId: String) extends Throwable(s"Purpose $purposeId not found")
  final case class PurposeVersionNotFound(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId not found for purpose $purposeId")
  final case class PurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId of purpose $purposeId is not in DRAFT")
  final case class PurposeVersionNotInWaitingForApproval(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId of purpose $purposeId is not in WAITING FOR APPROVAL")
  final case class PurposeVersionNotInExpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends Throwable(
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class PurposeVersionMissingRiskAnalysis(purposeId: String, versionId: String)
      extends Throwable(s"Missing Risk Analysis in Version $versionId of purpose $purposeId")
  final case class PurposeHasVersions(purposeId: String)
      extends Throwable(s"Purpose $purposeId has a non empty list of versions")

  final case class PurposeVersionStateConflict(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends Throwable(s"Version $versionId of Purpose $purposeId has a conflict state $state for this operation")

}
