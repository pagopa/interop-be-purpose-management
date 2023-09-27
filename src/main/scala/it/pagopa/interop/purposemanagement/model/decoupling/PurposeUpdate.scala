package it.pagopa.interop.purposemanagement.model.decoupling

import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.purposemanagement.model.PurposeUpdateContent
import it.pagopa.interop.purposemanagement.model.persistence.Adapters._
import it.pagopa.interop.purposemanagement.model.purpose.PersistentRiskAnalysisForm

import java.time.OffsetDateTime

final case class DraftPurposeVersionUpdate(dailyCalls: Integer, timestamp: OffsetDateTime)

final case class PurposeUpdate(
  title: String,
  description: String,
  isFreeOfCharge: Boolean,
  freeOfChargeReason: Option[String],
  riskAnalysisForm: Option[PersistentRiskAnalysisForm]
)

object PurposeUpdate {
  def fromApi(uuidSupplier: UUIDSupplier)(payload: PurposeUpdateContent): PurposeUpdate =
    PurposeUpdate(
      title = payload.title,
      description = payload.description,
      isFreeOfCharge = payload.isFreeOfCharge,
      freeOfChargeReason = payload.freeOfChargeReason,
      riskAnalysisForm = payload.riskAnalysisForm.map(PersistentRiskAnalysisForm.fromSeed(uuidSupplier))
    )
}
