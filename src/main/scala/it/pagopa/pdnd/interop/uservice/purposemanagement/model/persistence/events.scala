package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurpose

sealed trait Event extends Persistable

final case class PurposeAdded(purpose: PersistentPurpose)              extends Event
final case class PurposeActivated(purpose: PersistentPurpose)          extends Event
final case class PurposeSuspended(purpose: PersistentPurpose)          extends Event
final case class PurposeArchived(purpose: PersistentPurpose)           extends Event
final case class PurposeWaitingForApproval(purpose: PersistentPurpose) extends Event
