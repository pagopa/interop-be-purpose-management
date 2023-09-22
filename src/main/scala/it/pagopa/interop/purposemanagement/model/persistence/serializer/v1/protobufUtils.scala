package it.pagopa.interop.purposemanagement.model.persistence.serializer.v1

import cats.syntax.all._
import cats.implicits.toTraverseOps
import it.pagopa.interop.commons.utils.TypeConversions.{LongOps, OffsetDateTimeOps, StringOps}
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.purpose.{
  PurposeStateV1,
  PurposeV1,
  PurposeVersionDocumentV1,
  PurposeVersionV1
}
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.riskAnalysis.{
  RiskAnalysisFormV1,
  RiskAnalysisMultiAnswerV1,
  RiskAnalysisSingleAnswerV1
}
import it.pagopa.interop.purposemanagement.model.purpose._

import scala.util.{Failure, Success, Try}

object protobufUtils {

  final val FREE_OF_CHARGE_DEFAULT_REASON = "Sono una Pubblica Amministrazione"

  def toPersistentPurpose(protobufPurpose: PurposeV1): Either[Throwable, PersistentPurpose] = {
    val purpose = for {
      id               <- protobufPurpose.id.toUUID
      eserviceId       <- protobufPurpose.eserviceId.toUUID
      consumerId       <- protobufPurpose.consumerId.toUUID
      versions         <- protobufPurpose.versions.traverse(toPersistentPurposeVersion).toTry
      riskAnalysisForm <- protobufPurpose.riskAnalysisForm.traverse(toPersistentRiskAnalysis).toTry
      createdAt        <- protobufPurpose.createdAt.toOffsetDateTime
      updatedAt        <- protobufPurpose.updatedAt.traverse(_.toOffsetDateTime)
      isFreeOfCharge = protobufPurpose.isFreeOfCharge.getOrElse(true)
    } yield PersistentPurpose(
      id = id,
      eserviceId = eserviceId,
      consumerId = consumerId,
      versions = versions,
      suspendedByConsumer = protobufPurpose.suspendedByConsumer,
      suspendedByProducer = protobufPurpose.suspendedByProducer,
      title = protobufPurpose.title,
      description = protobufPurpose.description,
      riskAnalysisForm = riskAnalysisForm,
      createdAt = createdAt,
      updatedAt = updatedAt,
      isFreeOfCharge = isFreeOfCharge,
      freeOfChargeReason =
        Option.when(isFreeOfCharge)(protobufPurpose.freeOfChargeReason.getOrElse(FREE_OF_CHARGE_DEFAULT_REASON))
    )
    purpose.toEither
  }

  def toProtobufPurpose(persistentPurpose: PersistentPurpose): Either[Throwable, PurposeV1] =
    Right(
      PurposeV1(
        id = persistentPurpose.id.toString,
        eserviceId = persistentPurpose.eserviceId.toString,
        consumerId = persistentPurpose.consumerId.toString,
        versions = persistentPurpose.versions.map(toProtobufPurposeVersion),
        suspendedByConsumer = persistentPurpose.suspendedByConsumer,
        suspendedByProducer = persistentPurpose.suspendedByProducer,
        title = persistentPurpose.title,
        description = persistentPurpose.description,
        riskAnalysisForm = persistentPurpose.riskAnalysisForm.map(toProtobufRiskAnalysis),
        createdAt = persistentPurpose.createdAt.toMillis,
        updatedAt = persistentPurpose.updatedAt.map(_.toMillis),
        isFreeOfCharge = persistentPurpose.isFreeOfCharge.some,
        freeOfChargeReason = persistentPurpose.freeOfChargeReason
      )
    )

  def toPersistentPurposeVersion(
    protobufPurposeVersion: PurposeVersionV1
  ): Either[Throwable, PersistentPurposeVersion] = {
    val purpose = for {
      state                <- fromProtobufPurposeState(protobufPurposeVersion.state)
      id                   <- protobufPurposeVersion.id.toUUID
      createdAt            <- protobufPurposeVersion.createdAt.toOffsetDateTime
      updatedAt            <- protobufPurposeVersion.updatedAt.traverse(_.toOffsetDateTime)
      firstActivationAt    <- protobufPurposeVersion.firstActivationAt.traverse(_.toOffsetDateTime)
      expectedApprovalDate <- protobufPurposeVersion.expectedApprovalDate.traverse(_.toOffsetDateTime)
      riskAnalysisDoc      <- protobufPurposeVersion.riskAnalysis.traverse(toPersistentPurposeVersionDocument).toTry
      suspendedAt          <- protobufPurposeVersion.suspendedAt.traverse(_.toOffsetDateTime)
    } yield PersistentPurposeVersion(
      id = id,
      state = state,
      riskAnalysis = riskAnalysisDoc,
      dailyCalls = protobufPurposeVersion.dailyCalls,
      createdAt = createdAt,
      updatedAt = updatedAt,
      firstActivationAt = firstActivationAt,
      expectedApprovalDate = expectedApprovalDate,
      suspendedAt = suspendedAt
    )
    purpose.toEither
  }

  def toProtobufPurposeVersion(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersionV1 =
    PurposeVersionV1(
      id = persistentPurposeVersion.id.toString,
      state = toProtobufPurposeState(persistentPurposeVersion.state),
      dailyCalls = persistentPurposeVersion.dailyCalls,
      createdAt = persistentPurposeVersion.createdAt.toMillis,
      updatedAt = persistentPurposeVersion.updatedAt.map(_.toMillis),
      firstActivationAt = persistentPurposeVersion.firstActivationAt.map(_.toMillis),
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate.map(_.toMillis),
      riskAnalysis = persistentPurposeVersion.riskAnalysis.map(toProtobufPurposeVersionDocument),
      suspendedAt = persistentPurposeVersion.suspendedAt.map(_.toMillis)
    )

  def toProtobufPurposeState(status: PersistentPurposeVersionState): PurposeStateV1 = status match {
    case Draft              => PurposeStateV1.DRAFT
    case Active             => PurposeStateV1.ACTIVE
    case Suspended          => PurposeStateV1.SUSPENDED
    case Archived           => PurposeStateV1.ARCHIVED
    case WaitingForApproval => PurposeStateV1.WAITING_FOR_APPROVAL
  }

  def fromProtobufPurposeState(status: PurposeStateV1): Try[PersistentPurposeVersionState] = status match {
    case PurposeStateV1.DRAFT                => Success(Draft)
    case PurposeStateV1.ACTIVE               => Success(Active)
    case PurposeStateV1.SUSPENDED            => Success(Suspended)
    case PurposeStateV1.ARCHIVED             => Success(Archived)
    case PurposeStateV1.WAITING_FOR_APPROVAL => Success(WaitingForApproval)
    case PurposeStateV1.Unrecognized(value)  =>
      Failure(new RuntimeException(s"Protobuf PurposeStatus deserialization failed. Unrecognized value: $value"))
  }

  def toPersistentPurposeVersionDocument(
    protobufDocument: PurposeVersionDocumentV1
  ): Either[Throwable, PersistentPurposeVersionDocument] = {
    val purpose = for {
      id        <- protobufDocument.id.toUUID
      createdAt <- protobufDocument.createdAt.toOffsetDateTime
    } yield PersistentPurposeVersionDocument(
      id = id,
      contentType = protobufDocument.contentType,
      path = protobufDocument.path,
      createdAt = createdAt
    )
    purpose.toEither
  }

  def toProtobufPurposeVersionDocument(persistentDocument: PersistentPurposeVersionDocument): PurposeVersionDocumentV1 =
    PurposeVersionDocumentV1(
      id = persistentDocument.id.toString,
      contentType = persistentDocument.contentType,
      path = persistentDocument.path,
      createdAt = persistentDocument.createdAt.toMillis
    )

  def toPersistentRiskAnalysis(
    protobufRiskAnalysis: RiskAnalysisFormV1
  ): Either[Throwable, PersistentRiskAnalysisForm] =
    for {
      id             <- protobufRiskAnalysis.id.toUUID.toEither
      riskAnalysisId <- protobufRiskAnalysis.riskAnalysisId.traverse(_.toUUID).toEither
      singleAnswers  <- protobufRiskAnalysis.singleAnswers.traverse(toPersistentRiskAnalysisSingleAnswer)
      multiAnswers   <- protobufRiskAnalysis.multiAnswers.traverse(toPersistentRiskAnalysisMultiAnswer)
    } yield PersistentRiskAnalysisForm(
      id = id,
      riskAnalysisId = riskAnalysisId,
      version = protobufRiskAnalysis.version,
      singleAnswers = singleAnswers,
      multiAnswers = multiAnswers
    )

  def toProtobufRiskAnalysis(persistentRiskAnalysis: PersistentRiskAnalysisForm): RiskAnalysisFormV1 =
    RiskAnalysisFormV1(
      id = persistentRiskAnalysis.id.toString,
      riskAnalysisId = persistentRiskAnalysis.riskAnalysisId.map(_.toString),
      version = persistentRiskAnalysis.version,
      singleAnswers = persistentRiskAnalysis.singleAnswers.map(toProtobufRiskAnalysisSingleAnswer),
      multiAnswers = persistentRiskAnalysis.multiAnswers.map(toProtobufRiskAnalysisMultiAnswer)
    )

  def toPersistentRiskAnalysisSingleAnswer(
    protobufSingleAnswer: RiskAnalysisSingleAnswerV1
  ): Either[Throwable, PersistentRiskAnalysisSingleAnswer] = {
    val answers = for {
      id <- protobufSingleAnswer.id.toUUID
    } yield PersistentRiskAnalysisSingleAnswer(
      id = id,
      key = protobufSingleAnswer.key,
      value = protobufSingleAnswer.value
    )
    answers.toEither
  }

  def toProtobufRiskAnalysisSingleAnswer(
    persistentSingleAnswer: PersistentRiskAnalysisSingleAnswer
  ): RiskAnalysisSingleAnswerV1 =
    RiskAnalysisSingleAnswerV1(
      id = persistentSingleAnswer.id.toString,
      key = persistentSingleAnswer.key,
      value = persistentSingleAnswer.value
    )

  def toPersistentRiskAnalysisMultiAnswer(
    protobufMultiAnswer: RiskAnalysisMultiAnswerV1
  ): Either[Throwable, PersistentRiskAnalysisMultiAnswer] = {
    val answers = for {
      id <- protobufMultiAnswer.id.toUUID
    } yield PersistentRiskAnalysisMultiAnswer(
      id = id,
      key = protobufMultiAnswer.key,
      values = protobufMultiAnswer.values
    )
    answers.toEither
  }
  def toProtobufRiskAnalysisMultiAnswer(
    persistentMultiAnswer: PersistentRiskAnalysisMultiAnswer
  ): RiskAnalysisMultiAnswerV1 =
    RiskAnalysisMultiAnswerV1(
      id = persistentMultiAnswer.id.toString,
      key = persistentMultiAnswer.key,
      values = persistentMultiAnswer.values
    )
}
