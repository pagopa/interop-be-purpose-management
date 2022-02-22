package it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling

import it.pagopa.pdnd.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.DraftPurposeVersionUpdateContent

import java.time.OffsetDateTime

final case class DraftPurposeVersionUpdate(dailyCalls: Integer, timestamp: OffsetDateTime)

object DraftPurposeVersionUpdate {
  def fromApi(
    update: DraftPurposeVersionUpdateContent,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): DraftPurposeVersionUpdate =
    DraftPurposeVersionUpdate(dailyCalls = update.dailyCalls, timestamp = dateTimeSupplier.get)
}
