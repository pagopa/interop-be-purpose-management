package it.pagopa.interop.purposemanagement.model.purpose

import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.purposemanagement.model._

import java.util.UUID

final case class PersistentRiskAnalysisForm(
  id: UUID,
  version: String,
  singleAnswers: Seq[PersistentRiskAnalysisSingleAnswer],
  multiAnswers: Seq[PersistentRiskAnalysisMultiAnswer]
) {
  def toAPI: RiskAnalysisForm =
    RiskAnalysisForm(
      id = id,
      version = version,
      singleAnswers = singleAnswers.map(_.toAPI),
      multiAnswers = multiAnswers.map(_.toAPI)
    )

}

object PersistentRiskAnalysisForm {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisFormSeed): PersistentRiskAnalysisForm =
    PersistentRiskAnalysisForm(
      id = uuidSupplier.get,
      version = seed.version,
      singleAnswers = seed.singleAnswers.map(PersistentRiskAnalysisSingleAnswer.fromSeed(uuidSupplier)),
      multiAnswers = seed.multiAnswers.map(PersistentRiskAnalysisMultiAnswer.fromSeed(uuidSupplier))
    )
}

final case class PersistentRiskAnalysisSingleAnswer(id: UUID, key: String, value: Option[String]) {
  def toAPI: RiskAnalysisSingleAnswer =
    RiskAnalysisSingleAnswer(id = id, key = key, value = value)
}

object PersistentRiskAnalysisSingleAnswer {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisSingleAnswerSeed): PersistentRiskAnalysisSingleAnswer =
    PersistentRiskAnalysisSingleAnswer(id = uuidSupplier.get, key = seed.key, value = seed.value)
}

final case class PersistentRiskAnalysisMultiAnswer(id: UUID, key: String, values: Seq[String]) {
  def toAPI: RiskAnalysisMultiAnswer =
    RiskAnalysisMultiAnswer(id = id, key = key, values = values)
}

object PersistentRiskAnalysisMultiAnswer {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisMultiAnswerSeed): PersistentRiskAnalysisMultiAnswer =
    PersistentRiskAnalysisMultiAnswer(id = uuidSupplier.get, key = seed.key, values = seed.values)
}
