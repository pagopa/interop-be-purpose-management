package it.pagopa.interop.purposemanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentPurpose,
  toPersistentPurposeVersion,
  toProtobufPurpose,
  toProtobufPurposeVersion
}
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.state.{PurposesV1, StateV1}
import it.pagopa.interop.purposemanagement.model.purpose.PersistentPurpose

package object v1 {

  // type alias for traverse type inference
  type ThrowableOr[A] = Either[Throwable, A]

  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      for {
        purposes <- state.purposes
          .traverse[ThrowableOr, (String, PersistentPurpose)](entry =>
            toPersistentPurpose(entry.value).map(purpose => (entry.key, purpose))
          )
          .map(_.toMap)
      } yield State(purposes)
    }

  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      for {
        purposesV1 <- state.purposes.toSeq.traverse[ThrowableOr, PurposesV1] { case (key, purpose) =>
          toProtobufPurpose(purpose).map(value => PurposesV1(key, value))
        }
      } yield StateV1(purposesV1)
    }

  implicit def purposeCreatedV1PersistEventDeserializer: PersistEventDeserializer[PurposeCreatedV1, PurposeCreated] =
    event => toPersistentPurpose(event.purpose).map(PurposeCreated)

  implicit def purposeCreatedV1PersistEventSerializer: PersistEventSerializer[PurposeCreated, PurposeCreatedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeCreatedV1.of(ag))

  implicit def purposeUpdatedV1PersistEventDeserializer: PersistEventDeserializer[PurposeUpdatedV1, PurposeUpdated] =
    event => toPersistentPurpose(event.purpose).map(PurposeUpdated)

  implicit def purposeUpdatedV1PersistEventSerializer: PersistEventSerializer[PurposeUpdated, PurposeUpdatedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeUpdatedV1.of(ag))

  implicit def purposeVersionCreatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionCreatedV1, PurposeVersionCreated] =
    event =>
      for {
        version <- toPersistentPurposeVersion(event.version)
      } yield PurposeVersionCreated(event.purposeId, version)

  implicit def purposeVersionCreatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionCreated, PurposeVersionCreatedV1] =
    event => Right(PurposeVersionCreatedV1.of(event.purposeId, toProtobufPurposeVersion(event.version)))

  implicit def purposeVersionUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionUpdatedV1, PurposeVersionUpdated] =
    event =>
      for {
        version <- toPersistentPurposeVersion(event.version)
      } yield PurposeVersionUpdated(event.purposeId, version)

  implicit def purposeVersionUpdatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionUpdated, PurposeVersionUpdatedV1] =
    event => Right(PurposeVersionUpdatedV1.of(event.purposeId, toProtobufPurposeVersion(event.version)))

  implicit def purposeActivatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionActivated, PurposeVersionActivatedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeVersionActivatedV1.of(ag))

  implicit def purposeActivatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionActivatedV1, PurposeVersionActivated] =
    event => toPersistentPurpose(event.purpose).map(PurposeVersionActivated)

  implicit def purposeSuspendedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionSuspended, PurposeVersionSuspendedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeVersionSuspendedV1.of(ag))

  implicit def purposeSuspendedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionSuspendedV1, PurposeVersionSuspended] =
    event => toPersistentPurpose(event.purpose).map(PurposeVersionSuspended)

  implicit def purposeWaitingForApprovalV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionWaitedForApproval, PurposeVersionWaitedForApprovalV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeVersionWaitedForApprovalV1.of(ag))

  implicit def purposeWaitingForApprovalV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionWaitedForApprovalV1, PurposeVersionWaitedForApproval] =
    event => toPersistentPurpose(event.purpose).map(PurposeVersionWaitedForApproval)

  implicit def purposeArchivedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionArchived, PurposeVersionArchivedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeVersionArchivedV1.of(ag))

  implicit def purposeArchivedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionArchivedV1, PurposeVersionArchived] =
    event => toPersistentPurpose(event.purpose).map(PurposeVersionArchived)

  implicit def purposeVersionDeletedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionDeleted, PurposeVersionDeletedV1] =
    event => Right(PurposeVersionDeletedV1.of(event.purposeId, event.versionId))

  implicit def purposeVersionDeletedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionDeletedV1, PurposeVersionDeleted] =
    event => Right(PurposeVersionDeleted(event.purposeId, event.versionId))

  implicit def purposeDeletedV1PersistEventSerializer: PersistEventSerializer[PurposeDeleted, PurposeDeletedV1] =
    event => Right(PurposeDeletedV1.of(event.purposeId))

  implicit def purposeDeletedV1PersistEventDeserializer: PersistEventDeserializer[PurposeDeletedV1, PurposeDeleted] =
    event => Right(PurposeDeleted(event.purposeId))

}
