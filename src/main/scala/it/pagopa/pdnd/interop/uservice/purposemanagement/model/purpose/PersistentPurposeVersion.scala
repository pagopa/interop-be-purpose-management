package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{PurposeVersion, PurposeVersionSeed}
import it.pagopa.pdnd.interop.uservice.purposemanagement.service.OffsetDateTimeSupplier

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersion(
  id: UUID,
  state: PersistentPurposeVersionState,
  expectedApprovalDate: Option[OffsetDateTime],
  riskAnalysis: Option[PersistentPurposeVersionDocument],
  createdAt: OffsetDateTime
)

object PersistentPurposeVersion {
  def fromSeed(
    seed: PurposeVersionSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentPurposeVersion =
    PersistentPurposeVersion(
      id = uuidSupplier.get,
      state = PersistentPurposeVersionState.fromSeed(seed.state),
      createdAt = dateTimeSupplier.get,
      riskAnalysis =
        seed.riskAnalysis.map(PersistentPurposeVersionDocument.fromSeed(_, uuidSupplier, dateTimeSupplier)),
      expectedApprovalDate = None
    )

  def toAPI(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersion = {
    PurposeVersion(
      id = persistentPurposeVersion.id,
      state = persistentPurposeVersion.state.toApi,
      riskAnalysis = None,
      createdAt = persistentPurposeVersion.createdAt,
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate
    )
  }
}
