package it.pagopa.interop.purposemanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  lazy val queueUrl: String    = config.getString("purpose-management.queue-url")
  lazy val serverPort: Int     = config.getInt("purpose-management.port")
  val jwtAudience: Set[String] = config.getString("purpose-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")
  def projectionTag(index: Int)   = s"interop-be-purpose-management-persistence|$index"
  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
