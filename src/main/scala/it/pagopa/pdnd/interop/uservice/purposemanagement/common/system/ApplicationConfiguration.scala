package it.pagopa.pdnd.interop.uservice.purposemanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int          = config.getInt("purpose-management.port")
  lazy val jwtAudience: Set[String] = config.getStringList("purpose-management.jwt.audience").asScala.toSet

}
