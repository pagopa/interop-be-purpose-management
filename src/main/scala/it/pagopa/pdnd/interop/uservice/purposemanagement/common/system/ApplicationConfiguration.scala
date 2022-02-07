package it.pagopa.pdnd.interop.uservice.purposemanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int          = config.getInt("uservice-purpose-management.port")
  def jwtAudience: Set[String] = config.getStringList("uservice-purpose-management.jwt.audience").asScala.toSet

}
