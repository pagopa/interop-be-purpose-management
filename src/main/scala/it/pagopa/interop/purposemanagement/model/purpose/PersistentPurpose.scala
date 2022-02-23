package it.pagopa.interop.purposemanagement.model.purpose

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.model.decoupling.PurposeUpdate
import it.pagopa.interop.purposemanagement.model.{Purpose, PurposeSeed}

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurpose(
  id: UUID,
  eserviceId: UUID,
  consumerId: UUID,
  versions: Seq[PersistentPurposeVersion],
  suspendedByConsumer: Option[Boolean],
  suspendedByProducer: Option[Boolean],
  title: String,
  description: String,
  riskAnalysisForm: Option[PersistentRiskAnalysisForm],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime]
) {
  def toAPI: Purpose = {
    Purpose(
      id = id,
      eserviceId = eserviceId,
      consumerId = consumerId,
      versions = versions.map(_.toAPI),
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      title = title,
      description = description,
      riskAnalysisForm = riskAnalysisForm.map(_.toAPI),
      createdAt = createdAt,
      updatedAt = updatedAt
    )
  }

  def update(update: PurposeUpdate): PersistentPurpose =
    copy(title = update.title, description = update.description, riskAnalysisForm = update.riskAnalysisForm)

}

object PersistentPurpose {
  def fromSeed(
    seed: PurposeSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentPurpose =
    PersistentPurpose(
      id = uuidSupplier.get,
      eserviceId = seed.eserviceId,
      consumerId = seed.consumerId,
      versions = Seq.empty,
      suspendedByConsumer = None,
      suspendedByProducer = None,
      title = seed.title,
      description = seed.description,
      riskAnalysisForm = seed.riskAnalysisForm.map(PersistentRiskAnalysisForm.fromSeed(uuidSupplier)),
      createdAt = dateTimeSupplier.get,
      updatedAt = None
    )

}
