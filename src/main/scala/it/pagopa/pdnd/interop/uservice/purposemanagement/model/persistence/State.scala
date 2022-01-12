package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.PersistentPurpose

final case class State(purposes: Map[String, PersistentPurpose]) extends Persistable {
  def add(purpose: PersistentPurpose): State =
    copy(purposes = purposes + (purpose.id.toString -> purpose))

  def updatePurpose(purpose: PersistentPurpose): State = {
    copy(purposes = purposes + (purpose.id.toString -> purpose))
  }

}

object State {
  val empty: State = State(purposes = Map.empty[String, PersistentPurpose])
}
