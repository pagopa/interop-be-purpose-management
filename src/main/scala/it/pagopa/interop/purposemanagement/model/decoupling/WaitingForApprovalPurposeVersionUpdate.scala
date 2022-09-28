package it.pagopa.interop.purposemanagement.model.decoupling

import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.purposemanagement.model.WaitingForApprovalPurposeVersionUpdateContent

import java.time.OffsetDateTime

final case class WaitingForApprovalPurposeVersionUpdate(expectedApprovalDate: OffsetDateTime, timestamp: OffsetDateTime)

object WaitingForApprovalPurposeVersionUpdate {
  def fromApi(
    update: WaitingForApprovalPurposeVersionUpdateContent,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): WaitingForApprovalPurposeVersionUpdate =
    WaitingForApprovalPurposeVersionUpdate(
      expectedApprovalDate = update.expectedApprovalDate,
      timestamp = dateTimeSupplier.get()
    )
}
