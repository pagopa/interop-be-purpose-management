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

  final case class ActivatePurposeNotFound(purposeId: String)
      extends ComponentError("0006", s"Purpose $purposeId for version activation not found")
  final case class ActivatePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0007", s"Version $versionId of purpose $purposeId not found")
  final case class ActivatePurposeUnexpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0008",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class ActivatePurposeMissingRiskAnalysis(purposeId: String, versionId: String)
      extends ComponentError("0009", s"Missing Risk Analysis in Version $versionId of purpose $purposeId")
  final case class ActivatePurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0010", s"Error activating Version $versionId for Purpose $purposeId - Bad Request")

  final case class SuspendPurposeNotFound(purposeId: String)
      extends ComponentError("0011", s"Purpose $purposeId for version suspension not found")
  final case class SuspendPurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0012", s"Version $versionId of purpose $purposeId not found")
  final case class SuspendPurposeUnexpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0013",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class SuspendPurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0014", s"Error suspending Version $versionId for Purpose $purposeId - Bad Request")

  final case class ArchivePurposeNotFound(purposeId: String)
      extends ComponentError("0015", s"Purpose $purposeId for version archiving not found")
  final case class ArchivePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0016", s"Version $versionId of purpose $purposeId not found")
  final case class ArchivePurposeUnexpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0017",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class ArchivePurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0018", s"Error archiving Version $versionId for Purpose $purposeId - Bad Request")

  final case class WaitForApprovalPurposeNotFound(purposeId: String)
      extends ComponentError("0019", s"Purpose $purposeId for version wait for approval not found")
  final case class WaitForApprovalPurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0020", s"Version $versionId of purpose $purposeId not found")
  final case class WaitForApprovalPurposeUnexpectedState(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0021",
        s"State cannot be changed for Version $versionId of purpose $purposeId - Invalid starting state $state"
      )
  final case class WaitForApprovalPurposeMissingRiskAnalysis(purposeId: String, versionId: String)
      extends ComponentError("0022", s"Missing Risk Analysis in Version $versionId of purpose $purposeId")
  final case class WaitForApprovalPurposeBadRequest(purposeId: String, versionId: String)
      extends ComponentError(
        "0023",
        s"Error waiting for approval for Version $versionId for Purpose $purposeId - Bad Request"
      )

  case object GetPurposesBadRequest extends ComponentError("0024", "Error while getting purposes - Bad Request")

  final case class DocumentCreationPurposeNotFound(purposeId: String)
      extends ComponentError("0025", s"Error on document creation - Purpose $purposeId not found")
  final case class DocumentCreationVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0026", s"Error on document creation - Version $versionId of Purpose $purposeId not found")
  final case class DocumentCreationVersionNotInDraft(purposeId: String, versionId: String)
      extends ComponentError(
        "0027",
        s"Error on document creation - Version $versionId of Purpose $purposeId is not in DRAFT"
      )
  final case class DocumentCreationFailed(purposeId: String, versionId: String)
      extends ComponentError("0028", s"Error on document creation - Version $versionId of Purpose $purposeId")

  final case class UpdatePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError("0029", s"Error on version update - Version $versionId of purpose $purposeId not found")
  final case class UpdatePurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends ComponentError(
        "0030",
        s"Error on version update - Version $versionId of purpose $purposeId is not in Draft state"
      )
  final case class UpdatePurposeVersionBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0031", s"Error while updating version $versionId of purpose $purposeId - Bad Request")

  final case class CreatePurposeVersionStateConflict(
    purposeId: String,
    versionId: String,
    state: PersistentPurposeVersionState
  ) extends ComponentError(
        "0032",
        s"Version $versionId of Purpose $purposeId has a conflict state $state for this operation"
      )
  final case class CreatePurposeVersionNotFound(purposeId: String)
      extends ComponentError("0033", s"Purpose $purposeId not found on version creation")

  final case class DeletePurposeVersionNotFound(purposeId: String, versionId: String)
      extends ComponentError(
        "0034",
        s"Error deleting Version $versionId of Purpose $purposeId - Purpose of Version not found"
      )
  final case class DeletePurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends ComponentError("0035", s"Error deleting Version $versionId of Purpose $purposeId - Version not in Draft")
  final case class DeletePurposeVersionBadRequest(purposeId: String, versionId: String)
      extends ComponentError("0036", s"Error deleting Version $versionId of Purpose $purposeId - Bad Request")

  final case class DeletePurposeNotFound(purposeId: String)
      extends ComponentError("0037", s"Purpose $purposeId not found on purpose deletion")
  final case class DeletePurposeVersionsNotEmpty(purposeId: String)
      extends ComponentError("0038", s"Error deleting Purpose $purposeId - Purpose contains versions")
  final case class DeletePurposeBadRequest(purposeId: String)
      extends ComponentError("0039", s"Error deleting Purpose $purposeId - Bad Request")

  final case class UnexpectedMissingVersion(purposeId: String, versionId: String)
      extends ComponentError("0040", s"Version $versionId not found in Purpose $purposeId")

}
