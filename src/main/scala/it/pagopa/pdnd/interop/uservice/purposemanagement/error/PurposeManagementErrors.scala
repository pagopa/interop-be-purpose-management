package it.pagopa.pdnd.interop.uservice.purposemanagement.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object PurposeManagementErrors {
  case object CreatePurposeConflict   extends ComponentError("0001", "Purpose already existing")
  case object CreatePurposeBadRequest extends ComponentError("0002", "Error while creating purpose - bad request")

  case object GetPurposeNotFound   extends ComponentError("0003", "Purpose not found")
  case object GetPurposeBadRequest extends ComponentError("0004", "Error while retrieving purpose - bad request")

  case object CreatePurposeVersionBadRequest
      extends ComponentError("0005", "Error while creating purpose version - bad request")

  case object ActivatePurposeNotFound extends ComponentError("0006", "Purpose to activate not found")
  case object SuspendPurposeNotFound  extends ComponentError("0007", "Purpose to suspend not found")
  case object ArchivePurposeNotFound  extends ComponentError("0008", "Purpose to archive not found")

  case object GetPurposesBadRequest extends ComponentError("0009", "Error while getting purposes - bad request")

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

  case object UpdatePurposeVersionBadRequest
      extends ComponentError("0014", "Error while updating purpose version - bad request")

}
