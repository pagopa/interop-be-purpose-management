package it.pagopa.interop.purposemanagement.model.purpose

sealed trait PersistentStateChangeDetails

object PersistentStateChangeDetails {
  case object Consumer extends PersistentStateChangeDetails
  case object Producer extends PersistentStateChangeDetails
}
