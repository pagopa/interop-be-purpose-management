package it.pagopa.interop.purposemanagement

import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import it.pagopa.interop.commons.cqrs.model.{CqrsMetadata, MongoDbConfig}
import it.pagopa.interop.purposemanagement.common.system.ApplicationConfiguration
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ConnectionString, Document, MongoClient, MongoClientSettings}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

// Testing only the projection is not possible (https://github.com/akka/akka-projection/issues/454)

trait ItCqrsSpec extends AnyWordSpecLike with TestContainersForAll {

  private var internalMongodbClient: Option[MongoClient] = None
  private val mongoDbConfig: MongoDbConfig               = ApplicationConfiguration.mongoDb

  def startServer(): Unit
  def shutdownServer(): Unit

  override type Containers = DockerComposeContainer

  override def startContainers(): Containers =
    // Not able to use prepared PostgreSQL and MongoDB containers because ports are randomly generated, but
    //   akka testkit needs to load configuration at startup, so there is no time to override db configs
    DockerComposeContainer
      .Def(composeFiles = ComposeFile(Left(new File("src/it/resources/docker-compose-it.yaml"))))
      .start()

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)

    internalMongodbClient = Some(
      MongoClient(
        MongoClientSettings
          .builder()
          .applyConnectionString(new ConnectionString(mongoDbConfig.connectionString))
          .codecRegistry(DEFAULT_CODEC_REGISTRY)
          .streamFactoryFactory(NettyStreamFactoryFactory())
          .build()
      )
    )

    startServer()
  }

  def mongodbClient: MongoClient = internalMongodbClient match {
    case Some(client) => client
    case None         => throw new Exception("MongoDB client not yet initialized")
  }

  def findOne[T: JsonReader](id: String)(implicit ec: ExecutionContext): Future[T] = find[T](id).map(_.head)

  def find[T: JsonReader](id: String)(implicit ec: ExecutionContext): Future[Seq[T]] = for {
    // Wait a reasonable amount of time to allow the event to be processed by the projection
    _       <- Future.successful(Thread.sleep(2500))
    results <- mongodbClient
      .getDatabase(mongoDbConfig.dbName)
      .getCollection(mongoDbConfig.collectionName)
      .find(Filters.eq("data.id", id))
      .toFuture()
  } yield results.map(extractData[T])

  private def extractData[T: JsonReader](document: Document): T = {
    val fields = document.toJson().parseJson.asJsObject.getFields("data", "metadata")
    fields match {
      case data :: metadata :: Nil =>
        val cqrsMetadata = metadata.convertTo[CqrsMetadata]

        assert(cqrsMetadata.sourceEvent.persistenceId.nonEmpty)
        assert(cqrsMetadata.sourceEvent.sequenceNr >= 0)
        assert(cqrsMetadata.sourceEvent.timestamp > 0)

        data.convertTo[T]
      case _                       => fail(s"Unexpected number of fields ${fields.size}. Content: $fields")
    }
  }

  override def beforeContainersStop(containers: Containers): Unit = {
    mongodbClient.getDatabase(mongoDbConfig.dbName).drop().toFuture().futureValue
    shutdownServer()
    internalMongodbClient.get.close()
    super.afterContainersStart(containers)
  }

}
