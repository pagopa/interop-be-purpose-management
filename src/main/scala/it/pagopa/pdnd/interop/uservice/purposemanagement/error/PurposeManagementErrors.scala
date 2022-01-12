package it.pagopa.pdnd.interop.uservice.purposemanagement.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object PurposeManagementErrors {
  case object AddPurposeConflict   extends ComponentError("0001", "Purpose already existing")
  case object AddPurposeBadRequest extends ComponentError("0002", "Error while creating purpose - bad request")

  case object GetPurposeNotFound   extends ComponentError("0003", "Purpose not found")
  case object GetPurposeBadRequest extends ComponentError("0004", "Error while retrieving purpose - bad request")

  case object ActivatePurposeNotFound extends ComponentError("0005", "Purpose to activate not found")
  case object SuspendPurposeNotFound  extends ComponentError("0006", "Purpose to suspend not found")
  case object ArchivePurposeNotFound  extends ComponentError("0007", "Purpose to archive not found")

  case object GetPurposesBadRequest extends ComponentError("0008", "Error while getting purposes - bad request")
}
