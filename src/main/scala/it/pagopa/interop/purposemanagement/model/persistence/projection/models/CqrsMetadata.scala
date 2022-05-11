package it.pagopa.interop.purposemanagement.model.persistence.projection.models

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

final case class CqrsMetadata(sourceEvent: SourceEvent)

object CqrsMetadata {
  implicit val cqrsMetadataFormat: RootJsonFormat[CqrsMetadata] = jsonFormat1(CqrsMetadata.apply)
}

final case class SourceEvent(persistenceId: String, sequenceNr: Long, timestamp: Long)

object SourceEvent {
  implicit val sourceEventFormat: RootJsonFormat[SourceEvent] = jsonFormat3(SourceEvent.apply)
}
