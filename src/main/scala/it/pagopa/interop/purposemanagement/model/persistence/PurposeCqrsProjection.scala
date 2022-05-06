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
import cats.syntax.all._
import it.pagopa.interop.purposemanagement.model.persistence.JsonFormats._
import it.pagopa.interop.purposemanagement.model.purpose.PersistentPurpose
import org.mongodb.scala.model.{Filters, UpdateOptions, Updates}
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{Document, MongoClient, MongoCollection, SingleObservable}
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext

final case class PurposeCqrsProjection(offsetDbConfig: DatabaseConfig[JdbcProfile])(implicit
  system: ActorSystem[_],
  ec: ExecutionContext
) {

  // TODO User needs to be created in the default database, or else and auth error is received upon connection
  private val uri: String         = "mongodb://root:password@localhost/admin?retryWrites=true&w=majority"
  System.setProperty("org.mongodb.async.type", "netty")
  private val client: MongoClient = MongoClient(uri)

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId("purpose-cqrs-projections", tag),
    sourceProvider = sourceProvider(tag),
    handler = () => new CqrsProjectionHandler(client, "myTestDatabase", "purposes"),
    databaseConfig = offsetDbConfig
  )
}

// TODO pass db config object for db and collection names
final class CqrsProjectionHandler(client: MongoClient, dbName: String, collectionName: String)(implicit
  ec: ExecutionContext
) extends SlickHandler[EventEnvelope[Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  // The implementation is not idempotent
  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = DBIOAction.from {
    logger.debug(s"CQRS Projection: writing event with envelop $envelope")
    val collection: MongoCollection[Document] = client.getDatabase(dbName).getCollection(collectionName)

    val result = envelope.event match {
      case PurposeCreated(p)                  => collection.insertOne(Document(s"{data: ${p.toJson.compactPrint}}"))
      case PurposeUpdated(p)                  =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), Updates.set("data", Document(p.toJson.compactPrint)))
      case PurposeVersionCreated(pId, v)      =>
        collection.updateOne(Filters.eq("data.id", pId), Updates.push("data.versions", Document(v.toJson.compactPrint)))
      case _: PurposeVersionActivated         => ???
      case _: PurposeVersionSuspended         => ???
      case _: PurposeVersionWaitedForApproval => ???
      case _: PurposeVersionArchived          => ???
      case _: PurposeVersionUpdated           => ???
      case _: PurposeVersionDeleted           => ???
      case _: PurposeDeleted                  => ???
    }

    result.toFuture().as(Done)
  }

  def upsert(collection: MongoCollection[Document], purpose: PersistentPurpose): SingleObservable[UpdateResult] =
    collection.updateOne(
      Filters.eq("id", purpose.id.toString),
      Document(purpose.toJson.compactPrint),
      UpdateOptions().upsert(true)
    )

}
