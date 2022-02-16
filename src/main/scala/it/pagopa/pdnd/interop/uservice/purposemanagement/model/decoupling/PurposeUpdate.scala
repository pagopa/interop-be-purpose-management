package it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeUpdateContent
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentRiskAnalysisForm

final case class PurposeUpdate(title: String, description: String, riskAnalysisForm: PersistentRiskAnalysisForm)

object PurposeUpdate {
  def fromApi(uuidSupplier: UUIDSupplier)(payload: PurposeUpdateContent): PurposeUpdate =
    PurposeUpdate(
      title = payload.title,
      description = payload.description,
      riskAnalysisForm = PersistentRiskAnalysisForm.fromSeed(uuidSupplier)(payload.riskAnalysisForm)
    )
}
