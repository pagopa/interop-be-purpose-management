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
import it.pagopa.interop.purposemanagement.common.system.MongoDbConfig
import it.pagopa.interop.purposemanagement.model.persistence.JsonFormats._
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.persistence.projection.models.{CqrsMetadata, SourceEvent}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.mongodb.scala.model._
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.dbio._
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

final case class PurposeCqrsProjection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig)(
  implicit
  system: ActorSystem[_],
  ec: ExecutionContext
) {

  private val client: MongoClient = MongoClient(
    MongoClientSettings
      .builder()
      .applyConnectionString(new ConnectionString(mongoDbConfig.connectionString))
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .streamFactoryFactory(NettyStreamFactoryFactory())
      .build()
  )

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[Event]] =
    EventSourcedProvider
      .eventsByTag[Event](system, readJournalPluginId = JdbcReadJournal.Identifier, tag = tag)

  def projection(tag: String): ExactlyOnceProjection[Offset, EventEnvelope[Event]] = SlickProjection.exactlyOnce(
    projectionId = ProjectionId("purpose-cqrs-projections", tag),
    sourceProvider = sourceProvider(tag),
    handler = () => CqrsProjectionHandler(client, mongoDbConfig.dbName, mongoDbConfig.collectionName),
    databaseConfig = offsetDbConfig
  )
}

final case class CqrsProjectionHandler(client: MongoClient, dbName: String, collectionName: String)(implicit
  ec: ExecutionContext
) extends SlickHandler[EventEnvelope[Event]] {

  private val logger = LoggerFactory.getLogger(getClass)

  // Note: the implementation is not idempotent
  override def process(envelope: EventEnvelope[Event]): DBIO[Done] = DBIOAction.from {
    logger.debug(s"CQRS Projection: writing event with envelop $envelope")
    val collection: MongoCollection[Document] = client.getDatabase(dbName).getCollection(collectionName)

    val metadata: CqrsMetadata = CqrsMetadata(sourceEvent =
      SourceEvent(
        persistenceId = envelope.persistenceId,
        sequenceNr = envelope.sequenceNr,
        timestamp = envelope.timestamp
      )
    )

    def withMetadata(op: Bson): Bson = Updates.combine(Updates.set("metadata", metadata.toDocument), op)

    val result = envelope.event match {
      case PurposeCreated(p)                  =>
        collection.insertOne(Document(s"{ data: ${p.toJson.compactPrint}, metadata: ${metadata.toJson.compactPrint} }"))
      case PurposeUpdated(p)                  =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), withMetadata(Updates.set("data", p.toDocument)))
      case PurposeVersionCreated(pId, v)      =>
        collection.updateOne(Filters.eq("data.id", pId), withMetadata(Updates.push("data.versions", v.toDocument)))
      case PurposeVersionActivated(p)         =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), withMetadata(Updates.set("data", p.toDocument)))
      case PurposeVersionSuspended(p)         =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), withMetadata(Updates.set("data", p.toDocument)))
      case PurposeVersionWaitedForApproval(p) =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), withMetadata(Updates.set("data", p.toDocument)))
      case PurposeVersionArchived(p)          =>
        collection.updateOne(Filters.eq("data.id", p.id.toString), withMetadata(Updates.set("data", p.toDocument)))
      case PurposeVersionUpdated(pId, v)      =>
        collection.updateOne(
          Filters.and(Filters.eq("data.id", pId), Filters.eq("data.versions.id", v.id.toString)),
          withMetadata(Updates.set("data.versions.$", v.toDocument))
        )
      case PurposeVersionDeleted(pId, vId)    =>
        collection.updateOne(
          Filters.eq("data.id", pId),
          withMetadata(Updates.pull("data.versions", Document(s"{ id : \"$vId\" }")))
        )
      case PurposeDeleted(pId)                => collection.deleteOne(Filters.eq("data.id", pId))
    }

    val futureResult = result.toFuture()

    futureResult.onComplete {
      case Failure(e) => logger.error(s"Error on CQRS sink for ${show(metadata)}", e)
      case Success(_) => logger.debug(s"CQRS sink completed for ${show(metadata)}")
    }
    futureResult.as(Done)
  }

  private def show(metadata: CqrsMetadata): String =
    s"(persistenceId: ${metadata.sourceEvent.persistenceId}, sequenceNr: ${metadata.sourceEvent.sequenceNr}, timestamp : ${metadata.sourceEvent.timestamp})"

  implicit class SerializableToDocument[T: JsonWriter](v: T) extends AnyRef {
    def toDocument = Document(v.toJson.compactPrint)
  }

}
