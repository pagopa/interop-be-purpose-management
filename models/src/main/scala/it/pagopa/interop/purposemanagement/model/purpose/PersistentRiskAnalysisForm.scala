package it.pagopa.interop.purposemanagement.model.purpose

import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.purposemanagement.model._
import java.util.UUID

object PersistentRiskAnalysisForm
final case class PersistentRiskAnalysisForm(
  id: UUID,
  version: String,
  singleAnswers: Seq[PersistentRiskAnalysisSingleAnswer],
  multiAnswers: Seq[PersistentRiskAnalysisMultiAnswer]
)

object PersistentRiskAnalysisSingleAnswer
final case class PersistentRiskAnalysisSingleAnswer(id: UUID, key: String, value: Option[String])
object PersistentRiskAnalysisMultiAnswer
final case class PersistentRiskAnalysisMultiAnswer(id: UUID, key: String, values: Seq[String])
