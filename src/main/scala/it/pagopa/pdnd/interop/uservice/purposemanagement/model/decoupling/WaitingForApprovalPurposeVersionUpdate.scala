package it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling

import java.time.OffsetDateTime
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.WaitingForApprovalPurposeVersionUpdateContent
import it.pagopa.pdnd.interop.commons.utils.service.OffsetDateTimeSupplier

final case class WaitingForApprovalPurposeVersionUpdate(expectedApprovalDate: OffsetDateTime, timestamp: OffsetDateTime)

object WaitingForApprovalPurposeVersionUpdate {
  def fromApi(
    update: WaitingForApprovalPurposeVersionUpdateContent,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): WaitingForApprovalPurposeVersionUpdate =
    WaitingForApprovalPurposeVersionUpdate(
      expectedApprovalDate = update.expectedApprovalDate,
      timestamp = dateTimeSupplier.get
    )
}
