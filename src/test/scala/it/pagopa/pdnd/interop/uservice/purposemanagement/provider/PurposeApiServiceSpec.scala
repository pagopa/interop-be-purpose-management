package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.PurposeManagementErrors.CreatePurposeVersionBadRequest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class PurposeApiServiceSpec extends BaseIntegrationSpec {

  "Creation of a new purpose" must {

    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)

      val response: Future[Purpose] = createPurpose(purposeId, purposeSeed)

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        createdAt = timestamp,
        updatedAt = None
      )

      response.futureValue shouldBe expected
    }

    "Creation of a new purpose version" must {

      "succeed if purpose exists" in {
        val purposeId  = UUID.randomUUID()
        val versionId  = UUID.randomUUID()
        val eServiceId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()

        val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)

        val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

        val response: Future[PurposeVersion] =
          for {
            _      <- createPurpose(purposeId, purposeSeed)
            result <- createPurposeVersion(purposeId, versionId, versionSeed)
          } yield result

        val expected =
          PurposeVersion(id = versionId, state = versionSeed.state, createdAt = timestamp, expectedApprovalDate = None)

        response.futureValue shouldBe expected
      }

      "fail if purpose does not exist" in {
        val purposeId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

        (() => mockUUIDSupplier.get).expects().returning(versionId).once()
        (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

        val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId/versions", versionSeed)

        val result = response.futureValue
        result.status shouldBe 400
        result.errors shouldBe Seq(problemErrorFromError(CreatePurposeVersionBadRequest))

      }
    }
  }

}
