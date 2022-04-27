package it.pagopa.interop.purposemanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.{SlickHandler, SlickProjection}
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import java.util.UUID
import it.pagopa.interop.commons.queue.message.Message
import it.pagopa.interop.purposemanagement.model.persistence.Event

import scala.concurrent.ExecutionContext
import cats.syntax.all._
import org.slf4j.LoggerFactory
import scala.util.{Success, Failure}

class PurposePersistentProjection(dbConfig: DatabaseConfig[JdbcProfile], queueWriter: QueueWriter)(implicit
  system: ActorSystem[_],
  ec: ExecutionContext
) {

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId("purpose-projections", tag),
    sourceProvider = sourceProvider(tag),
    handler = () => new ProjectionHandler(queueWriter),
    databaseConfig = dbConfig
  )
}

class ProjectionHandler(queueWriter: QueueWriter)(implicit ec: ExecutionContext)
    extends SlickHandler[EventEnvelope[Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  def innerSend(message: Message): DBIO[Done] = DBIOAction.from {
    val future = queueWriter.send(message)
    future.onComplete {
      case Failure(e) => logger.error(e.getMessage())
      case Success(_) => logger.debug(s"Wrote on queue: $message")
    }
    future.as(Done)
  }

  val message: EventEnvelope[Event] => ((String, ProjectableEvent) => Message) = envelope => { case (kind, event) =>
    Message(UUID.randomUUID(), envelope.persistenceId, envelope.sequenceNr, envelope.timestamp, kind, event)
  }

  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = {
    def send(kind: String, x: ProjectableEvent) = innerSend(message(envelope)(kind, x))
    val kind: String                            = PurposeEventsSerde.getKind(envelope.event)
    send(kind, envelope.event)
  }

}
