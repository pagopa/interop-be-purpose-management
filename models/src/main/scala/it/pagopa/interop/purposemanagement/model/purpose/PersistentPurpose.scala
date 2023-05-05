package it.pagopa.interop.purposemanagement.model.purpose

import java.time.OffsetDateTime
import java.util.UUID

object PersistentPurpose
final case class PersistentPurpose(
  id: UUID,
  eserviceId: UUID,
  consumerId: UUID,
  versions: Seq[PersistentPurposeVersion],
  suspendedByConsumer: Option[Boolean],
  suspendedByProducer: Option[Boolean],
  suspendedAt:Option[OffsetDateTime],
  title: String,
  description: String,
  riskAnalysisForm: Option[PersistentRiskAnalysisForm],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime]
)
