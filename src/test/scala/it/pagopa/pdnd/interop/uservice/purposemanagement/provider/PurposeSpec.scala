package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeSpec extends BaseIntegrationSpec {

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

  }

  "Retrieve of a purpose" must {

    "succeed if purpose exists" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          _       <- createPurposeVersion(purposeId, versionId, versionSeed)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq(
          PurposeVersion(id = versionId, state = versionSeed.state, createdAt = timestamp, expectedApprovalDate = None)
        ),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        createdAt = timestamp,
        updatedAt = None
      )

      response.futureValue shouldBe expected
    }

    "fail if purpose does not exist" in {
      val purposeId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId", HttpMethods.GET)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0003")
    }
  }

}
