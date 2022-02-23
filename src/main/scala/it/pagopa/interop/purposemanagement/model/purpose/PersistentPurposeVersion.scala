package it.pagopa.interop.purposemanagement.model.purpose

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.error.InternalErrors.{
  PurposeVersionMissingRiskAnalysis,
  PurposeVersionNotInExpectedState
}
import it.pagopa.interop.purposemanagement.model.purpose.PersistentPurposeVersion._
import it.pagopa.interop.purposemanagement.model.{PurposeVersion, PurposeVersionSeed}

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentPurposeVersion(
  id: UUID,
  state: PersistentPurposeVersionState,
  expectedApprovalDate: Option[OffsetDateTime],
  riskAnalysis: Option[PersistentPurposeVersionDocument],
  dailyCalls: Integer,
  createdAt: OffsetDateTime,
  updatedAt: Option[OffsetDateTime],
  firstActivationAt: Option[OffsetDateTime]
) {

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
    Either.cond(
      WAITABLE_FOR_APPROVAL_STATES.contains(state),
      (),
      PurposeVersionNotInExpectedState(purposeId, id.toString, state)
    )

  def isArchivable(purposeId: String): Either[Throwable, Unit] =
    Either.cond(ARCHIVABLE_STATES.contains(state), (), PurposeVersionNotInExpectedState(purposeId, id.toString, state))

  def toAPI: PurposeVersion = {
    PurposeVersion(
      id = id,
      state = state.toApi,
      riskAnalysis = riskAnalysis.map(_.toAPI),
      createdAt = createdAt,
      updatedAt = updatedAt,
      firstActivationAt = firstActivationAt,
      expectedApprovalDate = expectedApprovalDate,
      dailyCalls = dailyCalls
    )
  }
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
      dailyCalls = seed.dailyCalls,
      createdAt = dateTimeSupplier.get,
      updatedAt = None,
      firstActivationAt = None,
      riskAnalysis = seed.riskAnalysis.map(PersistentPurposeVersionDocument.fromAPI),
      expectedApprovalDate = None
    )

}
