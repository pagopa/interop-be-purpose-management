package it.pagopa.interop.purposemanagement.model.persistence.projection

import akka.Done
import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.{SlickHandler, SlickProjection}
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.commons.queue.message.{Message, ProjectableEvent}
import it.pagopa.interop.purposemanagement.model.persistence.{Event, PurposeEventsSerde}
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

final case class PurposeNotificationProjection(
  dbConfig: DatabaseConfig[JdbcProfile],
  queueWriter: QueueWriter,
  projectionId: String
)(implicit system: ActorSystem[_], ec: ExecutionContext) {

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId(projectionId, tag),
    sourceProvider = sourceProvider(tag),
    handler = () => NotificationProjectionHandler(queueWriter),
    databaseConfig = dbConfig
  )
}

final case class NotificationProjectionHandler(queueWriter: QueueWriter)(implicit ec: ExecutionContext)
    extends SlickHandler[EventEnvelope[Event]] {

  private val logger: Logger = Logger(this.getClass)

  def innerSend(message: Message): DBIO[Done] = DBIOAction.from {
    def show(m: Message): String = {
      val (persId, persNr, time) = (m.eventJournalPersistenceId, m.eventJournalSequenceNumber, m.eventTimestamp)
      s"message with persistenceId ${persId}, sequenceNr ${persNr} and timestamp ${time}"
    }

    val future = queueWriter.send(message)
    future.onComplete {
      case Failure(e) => logger.error(s"Error sending ${show(message)}", e)
      case Success(_) => logger.debug(s"Wrote on queue ${show(message)}")
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
