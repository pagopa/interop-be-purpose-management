package it.pagopa.interop.purposemanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int          = config.getInt("purpose-management.port")
  lazy val jwtAudience: Set[String] = config.getStringList("purpose-management.jwt.audience").asScala.toSet

  lazy val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")
  def projectionTag(index: Int)        = s"interop-be-purpose-management-persistence|$index"
  lazy val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

}
