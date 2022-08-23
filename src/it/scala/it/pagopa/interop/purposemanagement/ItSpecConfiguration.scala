package it.pagopa.interop.purposemanagement

import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.utils.{BEARER, USER_ROLES}

/** Selfless trait containing base test configuration for Akka Cluster Setup
  */
trait ItSpecConfiguration {

  val config: Config = ConfigFactory.load()

  def serviceURL: String =
    s"${config.getString("purpose-management.url")}${buildinfo.BuildInfo.interfaceVersion}"

}

object ItSpecConfiguration extends ItSpecConfiguration

//mocks admin user role rights for every call
object AdminMockAuthenticator extends Authenticator[Seq[(String, String)]] {
  override def apply(credentials: Credentials): Option[Seq[(String, String)]] = {
    credentials match {
      case Provided(identifier) => Some(Seq(BEARER -> identifier, USER_ROLES -> "admin"))
      case Missing              => None
    }
  }
}
