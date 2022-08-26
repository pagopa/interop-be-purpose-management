package it.pagopa.interop.purposemanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import it.pagopa.interop.commons.cqrs.service.DocumentConversions._
import it.pagopa.interop.purposemanagement.model.persistence.JsonFormats._
import it.pagopa.interop.purposemanagement.model.persistence._
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoCollection, _}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object PurposeCqrsProjection {
  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig, projectionId: String)(
    implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId = projectionId, eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case PurposeCreated(p)                  =>
      ActionWithDocument(collection.insertOne, Document(s"{ data: ${p.toJson.compactPrint} }"))
    case PurposeUpdated(p)                  =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", p.id.toString), _), Updates.set("data", p.toDocument))
    case PurposeVersionCreated(pId, v)      =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", pId), _), Updates.push("data.versions", v.toDocument))
    case PurposeVersionActivated(p)         =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", p.id.toString), _), Updates.set("data", p.toDocument))
    case PurposeVersionSuspended(p)         =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", p.id.toString), _), Updates.set("data", p.toDocument))
    case PurposeVersionWaitedForApproval(p) =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", p.id.toString), _), Updates.set("data", p.toDocument))
    case PurposeVersionArchived(p)          =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", p.id.toString), _), Updates.set("data", p.toDocument))
    case PurposeVersionUpdated(pId, v)      =>
      ActionWithBson(
        collection.updateMany(
          Filters.eq("data.id", pId),
          _,
          UpdateOptions().arrayFilters(List(Filters.eq("elem.id", v.id.toString)).asJava)
        ),
        Updates.set("data.versions.$[elem]", v.toDocument)
      )
    case PurposeVersionDeleted(pId, vId)    =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", pId), _),
        Updates.pull("data.versions", Document(s"{ id : \"$vId\" }"))
      )
    case PurposeDeleted(pId)                => Action(collection.deleteOne(Filters.eq("data.id", pId)))
  }
}
