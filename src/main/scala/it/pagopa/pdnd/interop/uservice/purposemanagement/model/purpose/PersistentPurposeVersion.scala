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
  createdAt: OffsetDateTime
)

object PersistentPurposeVersion {
  def fromAPI(
    seed: PurposeVersionSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentPurposeVersion =
    PersistentPurposeVersion(
      id = uuidSupplier.get,
      state = PersistentPurposeVersionState.fromApi(seed.state),
      createdAt = dateTimeSupplier.get,
      expectedApprovalDate = None
    )

  def toAPI(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersion = {
    PurposeVersion(
      id = persistentPurposeVersion.id,
      state = persistentPurposeVersion.state.toApi,
      createdAt = persistentPurposeVersion.createdAt,
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate
    )
  }
}
