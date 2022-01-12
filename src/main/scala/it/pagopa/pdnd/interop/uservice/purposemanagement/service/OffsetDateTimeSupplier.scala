package it.pagopa.pdnd.interop.uservice.purposemanagement.service

import java.time.OffsetDateTime

trait OffsetDateTimeSupplier {
  def get: OffsetDateTime
}
