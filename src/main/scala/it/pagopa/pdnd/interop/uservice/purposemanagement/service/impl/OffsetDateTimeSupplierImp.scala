package it.pagopa.pdnd.interop.uservice.purposemanagement.service.impl

import it.pagopa.pdnd.interop.uservice.purposemanagement.service.OffsetDateTimeSupplier

import java.time.OffsetDateTime

case object OffsetDateTimeSupplierImp extends OffsetDateTimeSupplier {
  override def get: OffsetDateTime = OffsetDateTime.now()
}
