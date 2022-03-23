package it.pagopa.interop.purposemanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.interop.purposemanagement.model.decoupling.{
  DraftPurposeVersionUpdate,
  PurposeUpdate,
  WaitingForApprovalPurposeVersionUpdate
}
import it.pagopa.interop.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import it.pagopa.interop.purposemanagement.model.{PurposeVersionDocument, StateChangeDetails}

sealed trait Command

case object Idle                                                                        extends Command
final case class CreatePurpose(purpose: PersistentPurpose, replyTo: ActorRef[StatusReply[PersistentPurpose]])
    extends Command
final case class GetPurpose(purposeId: String, replyTo: ActorRef[StatusReply[Option[PersistentPurpose]]])
    extends Command
final case class DeletePurpose(purposeId: String, replyTo: ActorRef[StatusReply[Unit]]) extends Command
final case class CreatePurposeVersion(
  purposeId: String,
  purposeVersion: PersistentPurposeVersion,
  replyTo: ActorRef[StatusReply[PersistentPurposeVersion]]
) extends Command
final case class DeletePurposeVersion(purposeId: String, versionId: String, replyTo: ActorRef[StatusReply[Unit]])
    extends Command
final case class UpdatePurpose(
  purposeId: String,
  update: PurposeUpdate,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command
final case class UpdateDraftPurposeVersion(
  purposeId: String,
  versionId: String,
  update: DraftPurposeVersionUpdate,
  replyTo: ActorRef[StatusReply[PersistentPurposeVersion]]
) extends Command
final case class UpdateWaitingForApprovalPurposeVersion(
  purposeId: String,
  versionId: String,
  update: WaitingForApprovalPurposeVersionUpdate,
  replyTo: ActorRef[StatusReply[PersistentPurposeVersion]]
) extends Command
final case class ActivatePurposeVersion(
  purposeId: String,
  versionId: String,
  riskAnalysis: Option[PurposeVersionDocument],
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command
final case class SuspendPurposeVersion(
  purposeId: String,
  versionId: String,
  stateChangeDetails: StateChangeDetails,
  replyTo: ActorRef[StatusReply[PersistentPurpose]]
) extends Command
final case class WaitForApprovalPurposeVersion(
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
