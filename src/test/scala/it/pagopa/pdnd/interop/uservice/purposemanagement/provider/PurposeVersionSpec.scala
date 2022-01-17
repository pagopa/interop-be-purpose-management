package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeVersionSpec extends BaseIntegrationSpec {

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

      val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0005")

    }
  }

  "Activation of purpose" must {
    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.DRAFT)

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/activate",
        HttpMethods.POST,
        StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER))
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0006")
    }
  }

  "Suspension of purpose" must {
    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.DRAFT)

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/suspend",
        HttpMethods.POST,
        StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER))
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0007")
    }
  }

  "Archive of purpose" must {
    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.DRAFT)

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/archive",
        HttpMethods.POST,
        StateChangeDetails(changedBy = Some(ChangedBy.CONSUMER))
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0008")
    }
  }

}
