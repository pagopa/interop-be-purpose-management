package it.pagopa.interop.purposemanagement.model.purpose

import java.time.OffsetDateTime
import java.util.UUID

object PersistentPurposeVersionDocument
final case class PersistentPurposeVersionDocument(
  id: UUID,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)
