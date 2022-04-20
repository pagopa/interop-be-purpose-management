package it.pagopa.interop.purposemanagement.model.persistence

import it.pagopa.interop.purposemanagement.model.purpose._
import it.pagopa.interop.commons.queue.message.ProjectableEvent

sealed trait Event extends Persistable with ProjectableEvent

final case class PurposeCreated(purpose: PersistentPurpose)                                  extends Event
final case class PurposeUpdated(purpose: PersistentPurpose)                                  extends Event
final case class PurposeVersionCreated(purposeId: String, version: PersistentPurposeVersion) extends Event
final case class PurposeVersionActivated(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionSuspended(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionWaitedForApproval(purpose: PersistentPurpose)                 extends Event
final case class PurposeVersionArchived(purpose: PersistentPurpose)                          extends Event
final case class PurposeVersionUpdated(purposeId: String, version: PersistentPurposeVersion) extends Event
final case class PurposeVersionDeleted(purposeId: String, versionId: String)                 extends Event
final case class PurposeDeleted(purposeId: String)                                           extends Event
