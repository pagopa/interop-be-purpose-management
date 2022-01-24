package it.pagopa.pdnd.interop.uservice.purposemanagement.error

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurposeVersionState

object InternalErrors {
  final case class PurposeNotFound(purposeId: String) extends Throwable(s"Purpose $purposeId not found")
  final case class PurposeVersionNotFound(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId not found for purpose $purposeId")
  final case class PurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId of purpose $purposeId is not in DRAFT")
  final case class PurposeVersionNotInExpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends Throwable(
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class PurposeVersionMissingRiskAnalysis(purposeId: String, versionId: String)
      extends Throwable(s"Missing Risk Analysis in Version $versionId of purpose $purposeId")

  final case class PurposeVersionInDraftExists(purposeId: String)
      extends Throwable(s"Version in status Draft already exists for Purpose $purposeId")

}
