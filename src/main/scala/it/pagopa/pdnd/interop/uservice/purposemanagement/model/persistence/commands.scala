package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeState}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{Purpose, StateChangeDetails}

sealed trait Command

case object Idle                                                                                 extends Command
final case class AddPurpose(purpose: PersistentPurpose, replyTo: ActorRef[StatusReply[Purpose]]) extends Command
final case class GetPurpose(purposeId: String, replyTo: ActorRef[StatusReply[Option[Purpose]]])  extends Command
final case class ActivatePurpose(
  purposeId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Purpose]]
) extends Command
final case class SuspendPurpose(
  purposeId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Purpose]]
) extends Command
final case class ArchivePurpose(
  purposeId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Purpose]]
) extends Command
final case class WaitForPurposeApproval(
  purposeId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[Purpose]]
) extends Command

final case class ListPurposes(
  from: Int,
  to: Int,
  consumerId: Option[String],
  eserviceId: Option[String],
  state: Option[PersistentPurposeState],
  replyTo: ActorRef[Seq[Purpose]]
) extends Command
