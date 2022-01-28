package it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling

import it.pagopa.pdnd.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersionUpdateContent

import java.time.OffsetDateTime

final case class PurposeVersionUpdate(dailyCalls: Integer, timestamp: OffsetDateTime)

object PurposeVersionUpdate {
  def fromApi(update: PurposeVersionUpdateContent, dateTimeSupplier: OffsetDateTimeSupplier): PurposeVersionUpdate =
    PurposeVersionUpdate(dailyCalls = update.dailyCalls, timestamp = dateTimeSupplier.get)
}
