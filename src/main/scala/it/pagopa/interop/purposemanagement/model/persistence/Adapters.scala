package it.pagopa.interop.purposemanagement.model.persistence

import it.pagopa.interop.commons.utils.service._
import it.pagopa.interop.purposemanagement.error.InternalErrors._
import it.pagopa.interop.purposemanagement.model._
import it.pagopa.interop.purposemanagement.model.decoupling._
import it.pagopa.interop.purposemanagement.model.purpose._

object Adapters {

  implicit class PersistentRiskAnalysisSingleAnswerWrapper(private val p: PersistentRiskAnalysisSingleAnswer)
      extends AnyVal {
    def toAPI: RiskAnalysisSingleAnswer =
      RiskAnalysisSingleAnswer(id = p.id, key = p.key, value = p.value)
  }

  implicit class PersistentRiskAnalysisSingleAnswerObjectWrapper(private val p: PersistentRiskAnalysisSingleAnswer.type)
      extends AnyVal {
    def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisSingleAnswerSeed): PersistentRiskAnalysisSingleAnswer =
      PersistentRiskAnalysisSingleAnswer(id = uuidSupplier.get, key = seed.key, value = seed.value)
  }

  implicit class PersistentRiskAnalysisMultiAnswerWrapper(private val p: PersistentRiskAnalysisMultiAnswer)
      extends AnyVal {
    def toAPI: RiskAnalysisMultiAnswer = RiskAnalysisMultiAnswer(id = p.id, key = p.key, values = p.values)
  }

  implicit class PersistentRiskAnalysisMultiAnswerObjectWrapper(private val p: PersistentRiskAnalysisMultiAnswer.type)
      extends AnyVal {
    def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisMultiAnswerSeed): PersistentRiskAnalysisMultiAnswer =
      PersistentRiskAnalysisMultiAnswer(id = uuidSupplier.get, key = seed.key, values = seed.values)
  }

  implicit class PersistentRiskAnalysisFormWrapper(private val p: PersistentRiskAnalysisForm) extends AnyVal {
    def toAPI: RiskAnalysisForm = RiskAnalysisForm(
      id = p.id,
      version = p.version,
      singleAnswers = p.singleAnswers.map(_.toAPI),
      multiAnswers = p.multiAnswers.map(_.toAPI)
    )
  }

  implicit class PersistentRiskAnalysisFormObjectWrapper(private val p: PersistentRiskAnalysisForm.type)
      extends AnyVal {
    def fromSeed(uuidSupplier: UUIDSupplier)(seed: RiskAnalysisFormSeed): PersistentRiskAnalysisForm =
      PersistentRiskAnalysisForm(
        id = uuidSupplier.get,
        version = seed.version,
        singleAnswers = seed.singleAnswers.map(PersistentRiskAnalysisSingleAnswer.fromSeed(uuidSupplier)),
        multiAnswers = seed.multiAnswers.map(PersistentRiskAnalysisMultiAnswer.fromSeed(uuidSupplier))
      )
  }

  implicit class PersistentPurposeVersionStateWrapper(private val p: PersistentPurposeVersionState) extends AnyVal {
    def toApi: PurposeVersionState = p match {
      case Draft              => PurposeVersionState.DRAFT
      case Active             => PurposeVersionState.ACTIVE
      case Suspended          => PurposeVersionState.SUSPENDED
      case Archived           => PurposeVersionState.ARCHIVED
      case WaitingForApproval => PurposeVersionState.WAITING_FOR_APPROVAL
    }
  }

  implicit class PersistentPurposeVersionStateObjectWrapper(private val p: PersistentPurposeVersionState.type)
      extends AnyVal {
    def fromSeed(status: PurposeVersionState): PersistentPurposeVersionState = status match {
      case PurposeVersionState.DRAFT                => Draft
      case PurposeVersionState.ACTIVE               => Active
      case PurposeVersionState.SUSPENDED            => Suspended
      case PurposeVersionState.ARCHIVED             => Archived
      case PurposeVersionState.WAITING_FOR_APPROVAL => WaitingForApproval
    }
  }

  implicit class PersistentPurposeVersionDocumentWrapper(private val p: PersistentPurposeVersionDocument)
      extends AnyVal {
    def toAPI: PurposeVersionDocument =
      PurposeVersionDocument(id = p.id, contentType = p.contentType, path = p.path, createdAt = p.createdAt)
  }

  implicit class PersistentPurposeVersionDocumentObjectWrapper(private val p: PersistentPurposeVersionDocument.type)
      extends AnyVal {
    def fromAPI(document: PurposeVersionDocument): PersistentPurposeVersionDocument = PersistentPurposeVersionDocument(
      id = document.id,
      contentType = document.contentType,
      path = document.path,
      createdAt = document.createdAt
    )
  }

  implicit class PersistentPurposeVersionWrapper(private val p: PersistentPurposeVersion) {

    val ACTIVABLE_STATES             = Seq(Draft, Suspended, WaitingForApproval)
    val SUSPENDABLE_STATES           = Seq(Active, Suspended)
    val WAITABLE_FOR_APPROVAL_STATES = Seq(Draft)
    val ARCHIVABLE_STATES            = Seq(Active, Suspended)

    def isActivable(purposeId: String): Either[Throwable, Unit] = for {
      _ <- Either.cond(
        ACTIVABLE_STATES.contains(p.state),
        (),
        PurposeVersionNotInExpectedState(purposeId, p.id.toString, p.state)
      )
      _ <- Either.cond(p.riskAnalysis.isDefined, (), PurposeVersionMissingRiskAnalysis(purposeId, p.id.toString))
    } yield ()

    def isSuspendable(purposeId: String): Either[Throwable, Unit] = Either.cond(
      SUSPENDABLE_STATES.contains(p.state),
      (),
      PurposeVersionNotInExpectedState(purposeId, p.id.toString, p.state)
    )

    def canWaitForApproval(purposeId: String): Either[Throwable, Unit] = Either.cond(
      WAITABLE_FOR_APPROVAL_STATES.contains(p.state),
      (),
      PurposeVersionNotInExpectedState(purposeId, p.id.toString, p.state)
    )

    def isArchivable(purposeId: String): Either[Throwable, Unit] =
      Either.cond(
        ARCHIVABLE_STATES.contains(p.state),
        (),
        PurposeVersionNotInExpectedState(purposeId, p.id.toString, p.state)
      )

    def toAPI: PurposeVersion = PurposeVersion(
      id = p.id,
      state = p.state.toApi,
      riskAnalysis = p.riskAnalysis.map(_.toAPI),
      createdAt = p.createdAt,
      updatedAt = p.updatedAt,
      firstActivationAt = p.firstActivationAt,
      expectedApprovalDate = p.expectedApprovalDate,
      dailyCalls = p.dailyCalls
    )
  }

  implicit class PersistentPurposeVersionObjectWrapper(private val p: PersistentPurposeVersion.type) extends AnyVal {
    def fromSeed(
      seed: PurposeVersionSeed,
      uuidSupplier: UUIDSupplier,
      dateTimeSupplier: OffsetDateTimeSupplier
    ): PersistentPurposeVersion = PersistentPurposeVersion(
      id = uuidSupplier.get,
      state = Draft,
      dailyCalls = seed.dailyCalls,
      createdAt = dateTimeSupplier.get,
      updatedAt = None,
      firstActivationAt = None,
      riskAnalysis = seed.riskAnalysis.map(PersistentPurposeVersionDocument.fromAPI),
      expectedApprovalDate = None
    )
  }

  implicit class PersistentPurposeWrapper(private val p: PersistentPurpose) extends AnyVal {
    def toAPI: Purpose = Purpose(
      id = p.id,
      eserviceId = p.eserviceId,
      consumerId = p.consumerId,
      versions = p.versions.map(_.toAPI),
      suspendedByConsumer = p.suspendedByConsumer,
      suspendedByProducer = p.suspendedByProducer,
      title = p.title,
      description = p.description,
      riskAnalysisForm = p.riskAnalysisForm.map(_.toAPI),
      createdAt = p.createdAt,
      updatedAt = p.updatedAt
    )

    def update(update: PurposeUpdate): PersistentPurpose =
      p.copy(title = update.title, description = update.description, riskAnalysisForm = update.riskAnalysisForm)
  }

  implicit class PersistentPurposeObjectWrapper(private val p: PersistentPurpose.type) extends AnyVal {
    def fromSeed(
      seed: PurposeSeed,
      uuidSupplier: UUIDSupplier,
      dateTimeSupplier: OffsetDateTimeSupplier
    ): PersistentPurpose = PersistentPurpose(
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

}
