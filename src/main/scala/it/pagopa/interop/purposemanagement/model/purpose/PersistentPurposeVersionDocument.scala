package it.pagopa.interop.purposemanagement.model.purpose

import it.pagopa.interop.purposemanagement.model.PurposeVersionDocument

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersionDocument(
  id: UUID,
  contentType: String,
  path: String,
  createdAt: OffsetDateTime
) {
  def toAPI: PurposeVersionDocument =
    PurposeVersionDocument(id = id, contentType = contentType, path = path, createdAt = createdAt)
}

object PersistentPurposeVersionDocument {

  def fromAPI(document: PurposeVersionDocument): PersistentPurposeVersionDocument =
    PersistentPurposeVersionDocument(
      id = document.id,
      contentType = document.contentType,
      path = document.path,
      createdAt = document.createdAt
    )

}
