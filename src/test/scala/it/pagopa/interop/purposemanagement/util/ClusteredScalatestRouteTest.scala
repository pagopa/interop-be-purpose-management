package it.pagopa.interop.purposemanagement.util

import akka.actor
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.commons.utils.USER_ROLES
import it.pagopa.interop.purposemanagement.model.persistence.Command
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Informing, Suite}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait ClusteredScalatestRouteTest extends ScalatestRouteTest with Matchers with Informing {
  suite: Suite =>
  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit                                         = ActorTestKit()
  implicit def testTypedSystem                             = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  val testAkkaSharding: ClusterSharding = ClusterSharding(testTypedSystem)

  implicit val executionContext: ExecutionContextExecutor = testTypedSystem.executionContext
  val classicSystem: actor.ActorSystem                    = testTypedSystem.classicSystem

  val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]]

  Cluster(testTypedSystem).manager ! Join(Cluster(testTypedSystem).selfMember.address)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val _ = testAkkaSharding.init(testPersistentEntity)
  }

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(testTypedSystem, 10.seconds)
    super.afterAll()
  }

  def validateAuthorization(endpoint: Endpoint, r: Seq[(String, String)] => Route): Unit = {
    endpoint.rolesInContexts.foreach(contexts => {
      validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })

    // given a fake role, check that its invocation is forbidden
    endpoint.invalidRoles.foreach(contexts => {
      invalidRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })
  }

  // when request occurs, check that it does not return neither 401 nor 403
  private def validRoleCheck(role: String, request: => HttpRequest, r: => Route) =
    request ~> r ~> check {
      status should not be StatusCodes.Unauthorized
      status should not be StatusCodes.Forbidden
      info(s"role $role is properly authorized")
    }

  // when request occurs, check that it forbids invalid role
  private def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route) = {
    request ~> r ~> check {
      status shouldBe StatusCodes.Forbidden
      info(s"role $role is properly forbidden since it is invalid")
    }
  }
}
