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
  rejectionReason: Option[String],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime],
  firstActivationAt: Option[OffsetDateTime],
  suspendedAt: Option[OffsetDateTime]
)
