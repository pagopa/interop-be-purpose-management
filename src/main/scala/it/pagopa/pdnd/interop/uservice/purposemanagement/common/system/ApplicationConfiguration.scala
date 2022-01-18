package it.pagopa.pdnd.interop.uservice.purposemanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("uservice-purpose-management.port")

  def storageContainer: String = config.getString("pdnd-interop-commons.storage.container")

}
