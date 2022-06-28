package it.pagopa.interop.purposemanagement

import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import com.typesafe.config.ConfigFactory
import it.pagopa.interop.commons.utils.{BEARER, USER_ROLES}

trait SpecConfiguration {
  val testData = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster

      akka.remote.classic.netty.tcp.port = 0
      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on

      akka.cluster.sharding.number-of-shards = 10

      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
    """)

  val config = ConfigFactory
    .parseResourcesAnySyntax("application-test")
    .withFallback(testData)
}

object SpecConfiguration extends SpecConfiguration

//mocks admin user role rights for every call
object AdminMockAuthenticator extends Authenticator[Seq[(String, String)]] {
  override def apply(credentials: Credentials): Option[Seq[(String, String)]] = {
    credentials match {
      case Provided(identifier) => Some(Seq(BEARER -> identifier, USER_ROLES -> "admin"))
      case Missing              => None
    }
  }
}
