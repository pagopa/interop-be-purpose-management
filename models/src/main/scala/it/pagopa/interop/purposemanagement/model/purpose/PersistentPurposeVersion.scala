package it.pagopa.interop.purposemanagement.model.purpose

import java.time.OffsetDateTime
import java.util.UUID

object PersistentPurposeVersion
final case class PersistentPurposeVersion(
  id: UUID,
  state: PersistentPurposeVersionState,
  expectedApprovalDate: Option[OffsetDateTime],
  riskAnalysis: Option[PersistentPurposeVersionDocument],
  dailyCalls: Int,
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime],
  firstActivationAt: Option[OffsetDateTime],
  suspendedAt: Option[OffsetDateTime]
)
