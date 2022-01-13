package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{Purpose, PurposeVersion, StateChangeDetails}

sealed trait Command

case object Idle                                                                                    extends Command
final case class CreatePurpose(purpose: PersistentPurpose, replyTo: ActorRef[StatusReply[Purpose]]) extends Command
final case class GetPurpose(purposeId: String, replyTo: ActorRef[StatusReply[Option[Purpose]]])     extends Command
final case class GetPurposeVersion(
  purposeId: String,
  versionId: String,
  replyTo: ActorRef[StatusReply[Option[PurposeVersion]]]
) extends Command
final case class CreatePurposeVersion(
  purposeId: String,
  purposeVersion: PersistentPurposeVersion,
  replyTo: ActorRef[StatusReply[PersistentPurposeVersion]]
) extends Command
final case class ActivatePurposeVersion(
  purposeId: String,
  versionId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command
final case class SuspendPurposeVersion(
  purposeId: String,
  versionId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command
final case class ArchivePurposeVersion(
  purposeId: String,
  versionId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command

final case class ListPurposes(
  from: Int,
  to: Int,
  consumerId: Option[String],
  eserviceId: Option[String],
  state: List[PersistentPurposeVersionState],
  replyTo: ActorRef[Seq[PersistentPurpose]]
) extends Command
