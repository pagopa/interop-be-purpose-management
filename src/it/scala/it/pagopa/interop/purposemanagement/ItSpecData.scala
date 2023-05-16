package it.pagopa.interop.purposemanagement

import it.pagopa.interop.purposemanagement.model.RiskAnalysisForm
import it.pagopa.interop.purposemanagement.model.purpose._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object ItSpecData {
  final val timestamp: OffsetDateTime = OffsetDateTime.of(2022, 12, 31, 11, 22, 33, 0, ZoneOffset.UTC)

  def persistentDocument: PersistentPurposeVersionDocument =
    PersistentPurposeVersionDocument(id = UUID.randomUUID(), contentType = "json", path = "path", createdAt = timestamp)

  def riskAnalysisForm: RiskAnalysisForm =
    RiskAnalysisForm(id = UUID.randomUUID(), version = UUID.randomUUID().toString, Seq.empty, Seq.empty)

  def persistentPurposeVersion: PersistentPurposeVersion = PersistentPurposeVersion(
    id = UUID.randomUUID(),
    state = Draft,
    expectedApprovalDate = Some(timestamp),
    riskAnalysis = Some(persistentDocument),
    dailyCalls = 1111,
    createdAt = timestamp,
    updatedAt = Some(timestamp),
    firstActivationAt = Some(timestamp),
    suspendedAt = None
  )

  def persistentRiskAnalysisForm: PersistentRiskAnalysisForm =
    PersistentRiskAnalysisForm(id = riskAnalysisForm.id, version = riskAnalysisForm.version, Seq.empty, Seq.empty)

  def persistentPurpose: PersistentPurpose = PersistentPurpose(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    versions = Seq(persistentPurposeVersion),
    suspendedByConsumer = Some(false),
    suspendedByProducer = Some(true),
    title = "A Title",
    description = "A description",
    riskAnalysisForm = Some(persistentRiskAnalysisForm),
    createdAt = timestamp,
    updatedAt = Some(timestamp),
    isFreeOfCharge = false,
    freeOfChargeReason = None
  )
}
