package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersionDocument

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersionDocument(
  id: UUID,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
)

object PersistentPurposeVersionDocument {

  def fromApi(document: PurposeVersionDocument): PersistentPurposeVersionDocument =
    PersistentPurposeVersionDocument(
      id = document.id,
      contentType = document.contentType,
      path = document.path,
      createdAt = document.createdAt
    )

}
