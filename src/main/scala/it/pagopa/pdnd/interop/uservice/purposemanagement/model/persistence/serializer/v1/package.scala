package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurpose
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.events.{
  PurposeActivatedV1,
  PurposeAddedV1,
  PurposeArchivedV1,
  PurposeSuspendedV1
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentPurpose,
  toProtobufPurpose
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1.state.{PurposesV1, StateV1}

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

  implicit def purposeAddedV1PersistEventDeserializer: PersistEventDeserializer[PurposeAddedV1, PurposeAdded] =
    event => toPersistentPurpose(event.purpose).map(PurposeAdded)

  implicit def purposeAddedV1PersistEventSerializer: PersistEventSerializer[PurposeAdded, PurposeAddedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeAddedV1.of(ag))

  implicit def purposeActivatedV1PersistEventSerializer: PersistEventSerializer[PurposeActivated, PurposeActivatedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeActivatedV1.of(ag))

  implicit def purposeActivatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeActivatedV1, PurposeActivated] =
    event => toPersistentPurpose(event.purpose).map(PurposeActivated)

  implicit def purposeSuspendedV1PersistEventSerializer: PersistEventSerializer[PurposeSuspended, PurposeSuspendedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeSuspendedV1.of(ag))

  implicit def purposeSuspendedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeSuspendedV1, PurposeSuspended] =
    event => toPersistentPurpose(event.purpose).map(PurposeSuspended)

  implicit def purposeDeactivatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeArchived, PurposeArchivedV1] =
    event => toProtobufPurpose(event.purpose).map(ag => PurposeArchivedV1.of(ag))

  implicit def purposeDeactivatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeArchivedV1, PurposeArchived] =
    event => toPersistentPurpose(event.purpose).map(PurposeArchived)

}
