package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeVersion}

final case class State(purposes: Map[String, PersistentPurpose]) extends Persistable {
  def addPurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

  def addPurposeVersion(purposeId: String, version: PersistentPurposeVersion): State =
    purposes.get(purposeId) match {
      case Some(purpose) =>
        val updatedVersions = purpose.versions.filter(_.id != version.id) :+ version
        val updatedPurpose  = purpose.copy(versions = updatedVersions)
        copy(purposes = purposes + (purpose.id.toString -> updatedPurpose))
      case None => this
    }

  def updatePurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

}

object State {
  val empty: State = State(purposes = Map.empty[String, PersistentPurpose])
}
