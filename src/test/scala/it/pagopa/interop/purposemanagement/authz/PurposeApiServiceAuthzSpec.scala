package it.pagopa.interop.purposemanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.impl.PurposeApiServiceImpl
import it.pagopa.interop.purposemanagement.api.impl.PurposeApiMarshallerImpl._
import it.pagopa.interop.purposemanagement.model.{
  ActivatePurposeVersionPayload,
  ChangedBy,
  DraftPurposeVersionUpdateContent,
  PurposeSeed,
  PurposeUpdateContent,
  PurposeVersionSeed,
  StateChangeDetails,
  WaitingForApprovalPurposeVersionUpdateContent
}
import it.pagopa.interop.purposemanagement.model.persistence.Command
import it.pagopa.interop.purposemanagement.server.impl.Main.purposePersistenceEntity
import it.pagopa.interop.purposemanagement.timestamp
import it.pagopa.interop.purposemanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID

class PurposeApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {
  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] = purposePersistenceEntity

  val service = PurposeApiServiceImpl(
    testTypedSystem,
    testAkkaSharding,
    testPersistentEntity,
    new UUIDSupplier {
      override def get: UUID = UUID.randomUUID()
    },
    dateTimeSupplier = new OffsetDateTimeSupplier {
      override def get: OffsetDateTime = OffsetDateTime.now()
    }
  )

  "Purpose api operation authorization spec" should {

    "accept authorized roles for createPurpose" in {
      val endpoint = AuthorizedRoutes.endpoints("createPurpose")
      val fakeSeed = PurposeSeed(
        eserviceId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        title = "???",
        description = "???",
        riskAnalysisForm = None,
        isFreeOfCharge = false,
        freeOfChargeReason = None
      )
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.createPurpose(fakeSeed) })
    }
    "accept authorized roles for getPurpose" in {
      val endpoint = AuthorizedRoutes.endpoints("getPurpose")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getPurpose("fakeSeed") })
    }
    "accept authorized roles for deletePurpose" in {
      val endpoint = AuthorizedRoutes.endpoints("deletePurpose")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deletePurpose("fakeSeed") })
    }
    "accept authorized roles for createPurposeVersion" in {
      val endpoint = AuthorizedRoutes.endpoints("createPurposeVersion")
      val fakeSeed = PurposeVersionSeed(1, None)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.createPurposeVersion("fakeSeed", fakeSeed) }
      )

    }
    "accept authorized roles for deletePurposeVersion" in {
      val endpoint = AuthorizedRoutes.endpoints("deletePurposeVersion")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.deletePurposeVersion("fakeSeed", "fake") }
      )
    }
    "accept authorized roles for activatePurposeVersion " in {
      val endpoint    = AuthorizedRoutes.endpoints("activatePurposeVersion")
      val fakePayload = ActivatePurposeVersionPayload(None, StateChangeDetails(ChangedBy.CONSUMER, timestamp))
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.activatePurposeVersion("fakeSeed", "fake", fakePayload) }
      )
    }

    "accept authorized roles for suspendPurposeVersion" in {
      val endpoint = AuthorizedRoutes.endpoints("suspendPurposeVersion")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.suspendPurposeVersion("fakeSeed", "fake", StateChangeDetails(ChangedBy.CONSUMER, timestamp))
        }
      )
    }
    "accept authorized roles for waitForApprovalPurposeVersion" in {
      val endpoint = AuthorizedRoutes.endpoints("waitForApprovalPurposeVersion")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.waitForApprovalPurposeVersion("fakeSeed", "fake", StateChangeDetails(ChangedBy.CONSUMER, timestamp))
        }
      )
    }
    "accept authorized roles for archivePurposeVersion" in {
      val endpoint = AuthorizedRoutes.endpoints("archivePurposeVersion")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.archivePurposeVersion("fake", "fake", StateChangeDetails(ChangedBy.CONSUMER, timestamp))
        }
      )
    }
    "accept authorized roles for getPurposes" in {
      val endpoint = AuthorizedRoutes.endpoints("getPurposes")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getPurposes(None, None, "fake") })
    }
    "accept authorized roles for updatePurpose" in {
      val endpoint    = AuthorizedRoutes.endpoints("updatePurpose")
      val fakeContent = PurposeUpdateContent("test", "Fake", None)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updatePurpose("fake", fakeContent) }
      )
    }
    "accept authorized roles for updateDraftPurposeVersion" in {
      val endpoint    = AuthorizedRoutes.endpoints("updateDraftPurposeVersion")
      val fakeContent = DraftPurposeVersionUpdateContent(1)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateDraftPurposeVersion("fake", "fake", fakeContent) }
      )
    }
    "accept authorized roles for updateWaitingForApprovalPurposeVersion" in {
      val endpoint    = AuthorizedRoutes.endpoints("updateWaitingForApprovalPurposeVersion")
      val fakeContent = WaitingForApprovalPurposeVersionUpdateContent(OffsetDateTime.now())
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] =>
          service.updateWaitingForApprovalPurposeVersion("fakeSeed", "fake", fakeContent)
        }
      )
    }
  }

}
