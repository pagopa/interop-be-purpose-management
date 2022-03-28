package it.pagopa.interop.purposemanagement.model.persistence

import it.pagopa.interop.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeVersion}

final case class State(purposes: Map[String, PersistentPurpose]) extends Persistable {

  def getPurposeVersion(purposeId: String, versionId: String): Option[PersistentPurposeVersion] =
    for {
      purpose <- purposes.get(purposeId)
      version <- purpose.versions.find(_.id.toString == versionId)
    } yield version

  def addPurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

  def addPurposeVersion(purposeId: String, version: PersistentPurposeVersion): State =
    purposes.get(purposeId) match {
      case Some(purpose) =>
        val updatedVersions = purpose.versions.filter(_.id != version.id) :+ version
        val updatedPurpose  = purpose.copy(versions = updatedVersions)
        copy(purposes = purposes + (purpose.id.toString -> updatedPurpose))
      case None          => this
    }

  def removePurpose(purposeId: String): State =
    copy(purposes = purposes.filter(_._1 != purposeId))

  def removePurposeVersion(purposeId: String, versionId: String): State =
    purposes.get(purposeId) match {
      case Some(purpose) =>
        val updatedVersions = purpose.versions.filter(_.id.toString != versionId)
        val updatedPurpose  = purpose.copy(versions = updatedVersions)
        copy(purposes = purposes + (purpose.id.toString -> updatedPurpose))
      case None          => this
    }

  def updatePurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

}

object State {
  val empty: State = State(purposes = Map.empty[String, PersistentPurpose])
}
