package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersion

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersion(
  id: UUID,
  state: PersistentPurposeVersionState,
  expectedApprovalDate: Option[OffsetDateTime],
  createdAt: OffsetDateTime
)

object PersistentPurposeVersion {
  def toAPI(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersion = {
    PurposeVersion(
      id = persistentPurposeVersion.id,
      state = persistentPurposeVersion.state.toApi,
      createdAt = persistentPurposeVersion.createdAt,
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate
    )
  }
}
