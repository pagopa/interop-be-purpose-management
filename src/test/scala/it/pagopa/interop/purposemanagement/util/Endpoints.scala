package it.pagopa.interop.purposemanagement.util

import akka.http.scaladsl.client.RequestBuilding.{Delete, Get, Post, Put}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.commons.utils.USER_ROLES
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.util.UUID

/**
 * Holds a set of the authz details for all the endpoints behind authorization layer
 * @param endpoints
 */
case class Endpoints(endpoints: Set[Endpoint]) {
  def endpointsMap: Map[String, Endpoint] = endpoints.map(e => e.route -> e).toMap
}

case class Endpoint(route: String, verb: String, roles: Seq[String]) {

  /**
   * returns the sequence of invalid roles
   * @return
   */
  def invalidRoles: Seq[Seq[(String, String)]] = {
    existingRoles
      .diff(roles)
      .map(role => Seq("bearer" -> "token", "uid" -> UUID.randomUUID().toString, USER_ROLES -> role))
  }

  /**
   * returns a sequence of request contexts, each of the entry contains a request context for a specific role
   */
  def rolesInContexts: Seq[Seq[(String, String)]] = {
    roles.map(role => Seq("bearer" -> "token", "uid" -> UUID.randomUUID().toString, USER_ROLES -> role))
  }

  /**
   * Returns the HTTP request instance for the corresponding route verb
   * @return
   */
  def asRequest: HttpRequest = verb match {
    case "GET"    => Get()
    case "POST"   => Post()
    case "DELETE" => Delete()
    case "PUT"    => Put()
    // TODO make me safer, if you please
  }
}

/**
 * Reads the test resource file named <code>authz.json</code> deserializing it in a proper data set.<br>
 * <b>Warning: for testing purposes only - totally unsafe</b> 
 */
object AuthorizedRoutes extends SprayJsonSupport {

  val lines = scala.io.Source.fromResource("authz.json").getLines().mkString

  implicit val endpointFormat: RootJsonFormat[Endpoint]   = jsonFormat3(Endpoint)
  implicit val endpointsFormat: RootJsonFormat[Endpoints] = jsonFormat1(Endpoints)

  implicit def fromEntityUnmarshallerClientSeed: FromEntityUnmarshaller[Endpoints] =
    sprayJsonUnmarshaller[Endpoints]

  val endpoints: Map[String, Endpoint] = lines.parseJson.convertTo[Endpoints].endpointsMap
}
