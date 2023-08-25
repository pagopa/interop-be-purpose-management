package it.pagopa.interop.purposemanagement.error

import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.purposemanagement.model.purpose.PersistentPurposeVersionState

import java.util.UUID

object PurposeManagementErrors {
  final case class PurposeCreationConflict(purposeId: UUID)
      extends ComponentError("0001", s"Purpose $purposeId already exists")

  final case class PurposeNotFound(purposeId: String) extends ComponentError("0002", s"Purpose $purposeId not found")

  final case class PurposeMissingRiskAnalysis(purposeId: String, versionId: String)
      extends ComponentError("0003", s"Missing Risk Analysis in Version $versionId of purpose $purposeId")

  final case class NotAllowedForPurposeVersionState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0004",
        s"Operation is not allowed on state $state for Version $versionId of Purpose $purposeId"
      )

  final case class PurposeVersionStateConflict(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0005",
        s"Operation is not allowed on state $state for Version $versionId of Purpose $purposeId"
      )

  final case class PurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0006", s"Version $versionId of Purpose $purposeId not found")

  final case class PurposeVersionsNotEmpty(purposeId: String)
      extends ComponentError("0007", s"Purpose $purposeId contains versions")

  final case class DraftPurposeVersionNotFound(purposeId: String)
      extends ComponentError("0008", s"Draft version of Purpose $purposeId not found")
}
