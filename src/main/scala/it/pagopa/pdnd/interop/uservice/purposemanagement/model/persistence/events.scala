package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionDocument
}

sealed trait Event extends Persistable

final case class PurposeCreated(purpose: PersistentPurpose)                                  extends Event
final case class PurposeVersionCreated(purposeId: String, version: PersistentPurposeVersion) extends Event
final case class PurposeVersionActivated(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionSuspended(purpose: PersistentPurpose)                         extends Event
final case class PurposeVersionArchived(purpose: PersistentPurpose)                          extends Event
final case class RiskAnalysisAdded(purposeId: String, versionId: String, document: PersistentPurposeVersionDocument)
    extends Event
