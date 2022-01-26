package it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose

import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.InternalErrors.{
  PurposeVersionMissingRiskAnalysis,
  PurposeVersionNotInExpectedState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling.PurposeVersionUpdate
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurposeVersion._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{PurposeVersion, PurposeVersionSeed}

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersion(
  id: UUID,
  state: PersistentPurposeVersionState,
  expectedApprovalDate: Option[OffsetDateTime],
  riskAnalysis: Option[PersistentPurposeVersionDocument],
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime]
) {
  def update(update: PurposeVersionUpdate): PersistentPurposeVersion =
    copy(
      riskAnalysis = update.riskAnalysis.map(PersistentPurposeVersionDocument.fromAPI),
      updatedAt = Some(update.timestamp)
    )

  def isActivable(purposeId: String): Either[Throwable, Unit] =
    for {
      _ <- Either.cond(
        ACTIVABLE_STATES.contains(state),
        (),
        PurposeVersionNotInExpectedState(purposeId, id.toString, state)
      )
      _ <- Either.cond(riskAnalysis.isDefined, (), PurposeVersionMissingRiskAnalysis(purposeId, id.toString))
    } yield ()

  def isSuspendable(purposeId: String): Either[Throwable, Unit] =
    Either.cond(SUSPENDABLE_STATES.contains(state), (), PurposeVersionNotInExpectedState(purposeId, id.toString, state))

  def canWaitForApproval(purposeId: String): Either[Throwable, Unit] =
    for {
      _ <- Either.cond(
        WAITABLE_FOR_APPROVAL_STATES.contains(state),
        (),
        PurposeVersionNotInExpectedState(purposeId, id.toString, state)
      )
      _ <- Either.cond(riskAnalysis.isDefined, (), PurposeVersionMissingRiskAnalysis(purposeId, id.toString))
    } yield ()

  def isArchivable(purposeId: String): Either[Throwable, Unit] =
    Either.cond(ARCHIVABLE_STATES.contains(state), (), PurposeVersionNotInExpectedState(purposeId, id.toString, state))
}

object PersistentPurposeVersion {
  val ACTIVABLE_STATES = Seq(
    PersistentPurposeVersionState.Draft,
    PersistentPurposeVersionState.Suspended,
    PersistentPurposeVersionState.WaitingForApproval
  )
  val SUSPENDABLE_STATES           = Seq(PersistentPurposeVersionState.Active, PersistentPurposeVersionState.Suspended)
  val WAITABLE_FOR_APPROVAL_STATES = Seq(PersistentPurposeVersionState.Draft)
  val ARCHIVABLE_STATES            = Seq(PersistentPurposeVersionState.Active, PersistentPurposeVersionState.Suspended)

  def fromSeed(
    seed: PurposeVersionSeed,
    uuidSupplier: UUIDSupplier,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): PersistentPurposeVersion =
    PersistentPurposeVersion(
      id = uuidSupplier.get,
      state = PersistentPurposeVersionState.Draft,
      createdAt = dateTimeSupplier.get,
      updatedAt = None,
      riskAnalysis = seed.riskAnalysis.map(PersistentPurposeVersionDocument.fromAPI),
      expectedApprovalDate = None
    )

  def toAPI(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersion = {
    PurposeVersion(
      id = persistentPurposeVersion.id,
      state = persistentPurposeVersion.state.toApi,
      riskAnalysis = persistentPurposeVersion.riskAnalysis.map(PersistentPurposeVersionDocument.toAPI),
      createdAt = persistentPurposeVersion.createdAt,
      updatedAt = persistentPurposeVersion.updatedAt,
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate
    )
  }
}
