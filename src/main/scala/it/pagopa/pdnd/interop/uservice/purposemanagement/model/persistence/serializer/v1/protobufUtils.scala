package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.{LongOps, OffsetDateTimeOps, StringOps}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.purpose.{
  PurposeStateV1,
  PurposeV1
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeState}

import scala.util.{Failure, Success, Try}

object protobufUtils {

  def toPersistentPurpose(protobufPurpose: PurposeV1): Either[Throwable, PersistentPurpose] = {
    val purpose = for {
      status     <- fromProtobufPurposeState(protobufPurpose.state)
      id         <- protobufPurpose.id.toUUID
      eserviceId <- protobufPurpose.eserviceId.toUUID
      consumerId <- protobufPurpose.consumerId.toUUID
      createdAt  <- protobufPurpose.createdAt.toOffsetDateTime
      updatedAt  <- protobufPurpose.updatedAt.traverse(_.toOffsetDateTime)
    } yield PersistentPurpose(
      id = id,
      eserviceId = eserviceId,
      consumerId = consumerId,
      state = status,
      suspendedByConsumer = protobufPurpose.suspendedByConsumer,
      suspendedByProducer = protobufPurpose.suspendedByProducer,
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
        state = toProtobufPurposeState(persistentPurpose.state),
        suspendedByConsumer = persistentPurpose.suspendedByConsumer,
        suspendedByProducer = persistentPurpose.suspendedByProducer,
        createdAt = persistentPurpose.createdAt.toMillis,
        updatedAt = persistentPurpose.updatedAt.map(_.toMillis)
      )
    )
  }

  def toProtobufPurposeState(status: PersistentPurposeState): PurposeStateV1 =
    status match {
      case PersistentPurposeState.Draft              => PurposeStateV1.DRAFT
      case PersistentPurposeState.Active             => PurposeStateV1.ACTIVE
      case PersistentPurposeState.Suspended          => PurposeStateV1.SUSPENDED
      case PersistentPurposeState.Archived           => PurposeStateV1.ARCHIVED
      case PersistentPurposeState.WaitingForApproval => PurposeStateV1.WAITING_FOR_APPROVAL
    }
  def fromProtobufPurposeState(status: PurposeStateV1): Try[PersistentPurposeState] =
    status match {
      case PurposeStateV1.DRAFT                => Success(PersistentPurposeState.Draft)
      case PurposeStateV1.ACTIVE               => Success(PersistentPurposeState.Active)
      case PurposeStateV1.SUSPENDED            => Success(PersistentPurposeState.Suspended)
      case PurposeStateV1.ARCHIVED             => Success(PersistentPurposeState.Archived)
      case PurposeStateV1.WAITING_FOR_APPROVAL => Success(PersistentPurposeState.WaitingForApproval)
      case PurposeStateV1.Unrecognized(value) =>
        Failure(new RuntimeException(s"Protobuf PurposeStatus deserialization failed. Unrecognized value: $value"))
    }

}
