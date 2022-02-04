package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{Purpose, PurposeSeed}

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
  description: Option[String],
  riskAnalysisForm: PersistentRiskAnalysisForm,
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime]
)

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
      riskAnalysisForm = PersistentRiskAnalysisForm.fromSeed(uuidSupplier)(seed.riskAnalysisForm),
      createdAt = dateTimeSupplier.get,
      updatedAt = None
    )

  def toAPI(persistentPurpose: PersistentPurpose): Purpose = {
    Purpose(
      id = persistentPurpose.id,
      eserviceId = persistentPurpose.eserviceId,
      consumerId = persistentPurpose.consumerId,
      versions = persistentPurpose.versions.map(PersistentPurposeVersion.toAPI),
      suspendedByConsumer = persistentPurpose.suspendedByConsumer,
      suspendedByProducer = persistentPurpose.suspendedByProducer,
      title = persistentPurpose.title,
      description = persistentPurpose.description,
      riskAnalysisForm = persistentPurpose.riskAnalysisForm.toAPI,
      createdAt = persistentPurpose.createdAt,
      updatedAt = persistentPurpose.updatedAt
    )
  }
}
