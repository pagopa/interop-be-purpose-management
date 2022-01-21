package it.pagopa.pdnd.interop.uservice.purposemanagement.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurposeVersionState

object PurposeManagementErrors {
  case object CreatePurposeConflict   extends ComponentError("0001", "Purpose already existing")
  case object CreatePurposeBadRequest extends ComponentError("0002", "Error while creating purpose - Bad Request")

  case object GetPurposeNotFound   extends ComponentError("0003", "Purpose not found")
  case object GetPurposeBadRequest extends ComponentError("0004", "Error while retrieving purpose - Bad Request")

  case object CreatePurposeVersionBadRequest
      extends ComponentError("0005", "Error while creating purpose version - Bad Request")

  case class ActivatePurposeNotFound(purposeId: String)
      extends ComponentError("0006", s"Purpose $purposeId for version activation not found")
  case class ActivatePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0007", s"Version $versionId of purpose $purposeId not found")
  case class ActivatePurposeUnexpectedState(purposeId: String, versionId: String, state: PersistentPurposeVersionState)
      extends ComponentError(
        "0008",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  case class ActivatePurposeMissingRiskAnalysis(purposeId: String, versionId: String)
      extends ComponentError("0009", s"Missing Risk Analysis in Version $versionId of purpose $purposeId")
  case class ActivatePurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0010", s"Error activating Version $versionId for Purpose $purposeId - Bad Request")

  case class SuspendPurposeNotFound(purposeId: String)
      extends ComponentError("0011", s"Purpose $purposeId for version suspension not found")
  case class SuspendPurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0012", s"Version $versionId of purpose $purposeId not found")
  case class SuspendPurposeUnexpectedState(purposeId: String, versionId: String, state: PersistentPurposeVersionState)
      extends ComponentError(
        "0013",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  case class SuspendPurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0014", s"Error suspending Version $versionId for Purpose $purposeId - Bad Request")

  case class ArchivePurposeNotFound(purposeId: String)
      extends ComponentError("0015", s"Purpose $purposeId for version archiving not found")
  case class ArchivePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0016", s"Version $versionId of purpose $purposeId not found")
  case class ArchivePurposeUnexpectedState(purposeId: String, versionId: String, state: PersistentPurposeVersionState)
      extends ComponentError(
        "0017",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  case class ArchivePurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0018", s"Error archiving Version $versionId for Purpose $purposeId - Bad Request")

  case class WaitForApprovalPurposeNotFound(purposeId: String)
      extends ComponentError("0019", s"Purpose $purposeId for version wait for approval not found")
  case class WaitForApprovalPurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0020", s"Version $versionId of purpose $purposeId not found")
  case class WaitForApprovalPurposeUnexpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0021",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  case class WaitForApprovalPurposeMissingRiskAnalysis(purposeId: String, versionId: String)
      extends ComponentError("0022", s"Missing Risk Analysis in Version $versionId of purpose $purposeId")
  case class WaitForApprovalPurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError(
        "0023",
        s"Error waiting for approval for Version $versionId for Purpose $purposeId - Bad Request"
      )

  case object GetPurposesBadRequest extends ComponentError("0009", "Error while getting purposes - Bad Request")

  case class DocumentCreationPurposeNotFound(purposeId: String)
      extends ComponentError("0010", s"Error on document creation - Purpose $purposeId not found")
  case class DocumentCreationVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0011", s"Error on document creation - Version $versionId of Purpose $purposeId not found")
  case class DocumentCreationVersionNotInDraft(purposeId: String, versionId: String)
      extends ComponentError(
        "0012",
        s"Error on document creation - Version $versionId of Purpose $purposeId is not in DRAFT"
      )
  case class DocumentCreationFailed(purposeId: String, versionId: String)
      extends ComponentError("0013", s"Error on document creation - Version $versionId of Purpose $purposeId")

  case class UpdatePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0014", s"Error on version update - Version $versionId of purpose $purposeId not found")
  case class UpdatePurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends ComponentError(
        "0015",
        s"Error on version update - Version $versionId of purpose $purposeId is not in Draft state"
      )
  case class UpdatePurposeVersionBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0016", s"Error while updating version $versionId of purpose $purposeId - Bad Request")

}
