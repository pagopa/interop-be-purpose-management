package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersionDocumentSeed
import it.pagopa.pdnd.interop.uservice.purposemanagement.service.OffsetDateTimeSupplier

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersionDocument(
  id: UUID,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)

object PersistentPurposeVersionDocument {

  def fromSeed(
    seed: PurposeVersionDocumentSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentPurposeVersionDocument =
    PersistentPurposeVersionDocument(
      id = uuidSupplier.get,
      contentType = seed.contentType,
      path = seed.path,
      createdAt = dateTimeSupplier.get
    )

}
