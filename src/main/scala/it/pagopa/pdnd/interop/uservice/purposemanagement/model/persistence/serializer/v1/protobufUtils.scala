package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{LongOps, OffsetDateTimeOps, StringOps}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.purpose.{
  PurposeStateV1,
  PurposeV1,
  PurposeVersionDocumentV1,
  PurposeVersionV1
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionDocument,
  PersistentPurposeVersionState
}

import scala.util.{Failure, Success, Try}

object protobufUtils {

  def toPersistentPurpose(protobufPurpose: PurposeV1): Either[Throwable, PersistentPurpose] = {
    val purpose = for {
      id         <- protobufPurpose.id.toUUID
      eserviceId <- protobufPurpose.eserviceId.toUUID
      consumerId <- protobufPurpose.consumerId.toUUID
      versions   <- protobufPurpose.versions.traverse(toPersistentPurposeVersion).toTry
      createdAt  <- protobufPurpose.createdAt.toOffsetDateTime
      updatedAt  <- protobufPurpose.updatedAt.traverse(_.toOffsetDateTime)
    } yield PersistentPurpose(
      id = id,
      eserviceId = eserviceId,
      consumerId = consumerId,
      versions = versions,
      suspendedByConsumer = protobufPurpose.suspendedByConsumer,
      suspendedByProducer = protobufPurpose.suspendedByProducer,
      title = protobufPurpose.title,
      description = protobufPurpose.description,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
    purpose.toEither
  }

  def toProtobufPurpose(persistentPurpose: PersistentPurpose): Either[Throwable, PurposeV1] = {
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
        createdAt = persistentPurpose.createdAt.toMillis,
        updatedAt = persistentPurpose.updatedAt.map(_.toMillis)
      )
    )
  }

  def toPersistentPurposeVersion(
    protobufPurposeVersion: PurposeVersionV1
  ): Either[Throwable, PersistentPurposeVersion] = {
    val purpose = for {
      state                <- fromProtobufPurposeState(protobufPurposeVersion.state)
      id                   <- protobufPurposeVersion.id.toUUID
      createdAt            <- protobufPurposeVersion.createdAt.toOffsetDateTime
      updatedAt            <- protobufPurposeVersion.updatedAt.traverse(_.toOffsetDateTime)
      expectedApprovalDate <- protobufPurposeVersion.expectedApprovalDate.traverse(_.toOffsetDateTime)
      riskAnalysisDoc      <- protobufPurposeVersion.riskAnalysis.traverse(toPersistentPurposeVersionDocument).toTry
    } yield PersistentPurposeVersion(
      id = id,
      state = state,
      riskAnalysis = riskAnalysisDoc,
      createdAt = createdAt,
      updatedAt = updatedAt,
      expectedApprovalDate = expectedApprovalDate
    )
    purpose.toEither
  }

  def toProtobufPurposeVersion(persistentPurposeVersion: PersistentPurposeVersion): PurposeVersionV1 =
    PurposeVersionV1(
      id = persistentPurposeVersion.id.toString,
      state = toProtobufPurposeState(persistentPurposeVersion.state),
      createdAt = persistentPurposeVersion.createdAt.toMillis,
      updatedAt = persistentPurposeVersion.updatedAt.map(_.toMillis),
      expectedApprovalDate = persistentPurposeVersion.expectedApprovalDate.map(_.toMillis)
    )

  def toProtobufPurposeState(status: PersistentPurposeVersionState): PurposeStateV1 =
    status match {
      case PersistentPurposeVersionState.Draft              => PurposeStateV1.DRAFT
      case PersistentPurposeVersionState.Active             => PurposeStateV1.ACTIVE
      case PersistentPurposeVersionState.Suspended          => PurposeStateV1.SUSPENDED
      case PersistentPurposeVersionState.Archived           => PurposeStateV1.ARCHIVED
      case PersistentPurposeVersionState.WaitingForApproval => PurposeStateV1.WAITING_FOR_APPROVAL
    }

  def fromProtobufPurposeState(status: PurposeStateV1): Try[PersistentPurposeVersionState] =
    status match {
      case PurposeStateV1.DRAFT                => Success(PersistentPurposeVersionState.Draft)
      case PurposeStateV1.ACTIVE               => Success(PersistentPurposeVersionState.Active)
      case PurposeStateV1.SUSPENDED            => Success(PersistentPurposeVersionState.Suspended)
      case PurposeStateV1.ARCHIVED             => Success(PersistentPurposeVersionState.Archived)
      case PurposeStateV1.WAITING_FOR_APPROVAL => Success(PersistentPurposeVersionState.WaitingForApproval)
      case PurposeStateV1.Unrecognized(value) =>
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

  def toProtobufPurposeVersionDocument(
    persistentDocument: PersistentPurposeVersionDocument
  ): PurposeVersionDocumentV1 = {
    PurposeVersionDocumentV1(
      id = persistentDocument.id.toString,
      contentType = persistentDocument.contentType,
      path = persistentDocument.path,
      createdAt = persistentDocument.createdAt.toMillis
    )
  }
}
