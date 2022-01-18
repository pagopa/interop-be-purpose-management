package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionDocument
}

final case class State(purposes: Map[String, PersistentPurpose]) extends Persistable {
  def addPurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

  def addPurposeVersion(purposeId: String, version: PersistentPurposeVersion): State =
    purposes.get(purposeId) match {
      case Some(purpose) =>
        val updatedPurpose = purpose.copy(versions = version +: purpose.versions)
        copy(purposes = purposes + (purpose.id.toString -> updatedPurpose))
      case None => this
    }

  def updatePurpose(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

  def addRiskAnalysis(purposeId: String, versionId: String, document: PersistentPurposeVersionDocument): State = {
    val updatedPurpose =
      for {
        purpose <- purposes.get(purposeId)
        version <- purpose.versions.find(_.id.toString == versionId)
        updatedVersion  = version.copy(riskAnalysis = Some(document))
        updatedVersions = purpose.versions.filter(_.id.toString != purposeId) :+ updatedVersion
      } yield purpose.copy(versions = updatedVersions)

    updatedPurpose match {
      case Some(p) =>
        copy(purposes = purposes + (purposeId -> p))
      case None =>
        this
    }

  }

}

object State {
  val empty: State = State(purposes = Map.empty[String, PersistentPurpose])
}
