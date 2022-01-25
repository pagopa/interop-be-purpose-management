package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeVersion}

sealed trait Event extends Persistable

final case class PurposeCreated(purpose: PersistentPurpose)                                  extends Event
final case class PurposeVersionCreated(purposeId: String, version: PersistentPurposeVersion) extends Event
final case class PurposeVersionActivated(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionSuspended(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionWaitedForApproval(purpose: PersistentPurpose)                 extends Event
final case class PurposeVersionArchived(purpose: PersistentPurpose)                          extends Event
final case class PurposeVersionUpdated(purposeId: String, version: PersistentPurposeVersion) extends Event
