package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.events._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentPurpose,
  toPersistentPurposeVersion,
  toProtobufPurpose,
  toProtobufPurposeVersion
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.state.{PurposesV1, StateV1}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurpose

package object v1 {

  //type alias for traverse type inference
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

  implicit def purposeVersionCreatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionCreatedV1, PurposeVersionCreated] =
    event =>
      for {
        version <- toPersistentPurposeVersion(event.version)
      } yield PurposeVersionCreated(event.purposeId, version)

  implicit def purposeVersionCreatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionCreated, PurposeVersionCreatedV1] =
    event => Right(PurposeVersionCreatedV1.of(event.purposeId, toProtobufPurposeVersion(event.version)))

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

  implicit def purposeDeactivatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeVersionArchived, PurposeVersionArchivedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeVersionArchivedV1.of(ag))

  implicit def purposeDeactivatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeVersionArchivedV1, PurposeVersionArchived] =
    event => toPersistentPurpose(event.purpose).map(PurposeVersionArchived)

}
