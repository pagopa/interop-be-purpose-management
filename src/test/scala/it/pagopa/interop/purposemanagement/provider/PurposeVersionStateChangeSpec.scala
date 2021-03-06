package it.pagopa.interop.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.purposemanagement._
import it.pagopa.interop.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeVersionStateChangeSpec extends BaseIntegrationSpec {

  "Activation of purpose" must {
    "succeed when risk analysis is added at creation" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          result  <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, None)
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ACTIVE,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp),
        riskAnalysis = Some(riskAnalysisDoc)
      )
      result shouldBe expected
    }

    "succeed when risk analysis is added at activation" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = None, dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          result  <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ACTIVE,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp),
        riskAnalysis = Some(riskAnalysisDoc)
      )
      result shouldBe expected
    }

    "succeed and archive old active version" in {
      val purposeId      = UUID.randomUUID()
      val versionId1     = UUID.randomUUID()
      val versionId2     = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          _       <- createPurposeVersion(purposeId, versionId1, versionSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expectedVersions = Seq(
        PurposeVersion(
          id = versionId1,
          state = PurposeVersionState.ARCHIVED,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          firstActivationAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc),
          dailyCalls = 100
        ),
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.ACTIVE,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          firstActivationAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc),
          dailyCalls = 100
        )
      )

      response.futureValue.versions should contain theSameElementsAs expectedVersions
    }

    "succeed and archive old suspended version" in {
      val purposeId      = UUID.randomUUID()
      val versionId1     = UUID.randomUUID()
      val versionId2     = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          _       <- createPurposeVersion(purposeId, versionId1, versionSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          _       <- suspendVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expectedVersions = Seq(
        PurposeVersion(
          id = versionId1,
          state = PurposeVersionState.ARCHIVED,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          firstActivationAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc),
          dailyCalls = 100
        ),
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.ACTIVE,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          firstActivationAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc),
          dailyCalls = 100
        )
      )

      response.futureValue.versions should contain theSameElementsAs expectedVersions
    }

    "succeed and update activation date only the first time" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val firstActivationAt  = timestamp.plusDays(10)
      val secondActivationAt = firstActivationAt.plusDays(10)

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          _ <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis, firstActivationAt)
          _ <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, None, secondActivationAt)
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ACTIVE,
        updatedAt = Some(secondActivationAt),
        firstActivationAt = Some(firstActivationAt)
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/activate",
        HttpMethods.POST,
        ActivatePurposeVersionPayload(stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.CONSUMER))
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0006")
    }

    "fail on missing risk analysis" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = None, dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            ActivatePurposeVersionPayload(stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.CONSUMER))
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0009")
    }

    "fail on wrong current version state" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          _      <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            ActivatePurposeVersionPayload(stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.CONSUMER))
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0008")
    }
  }

  "Suspension of purpose" must {
    "succeed" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          _       <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          result  <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.SUSPENDED,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp)
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/suspend",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0011")
    }

    "fail on wrong current version state" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/suspend",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0013")
    }
  }

  "Archive of purpose" must {
    "succeed" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          _       <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          result  <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ARCHIVED,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp)
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/archive",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0015")
    }

    "fail on wrong current version state" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/archive",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0017")
    }
  }

  "Wait for approval of purpose" must {
    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = None, dailyCalls = 100)

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          version <- createPurposeVersion(purposeId, versionId, versionSeed)
          result  <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (version, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.WAITING_FOR_APPROVAL,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp)
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/waitForApproval",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0019")
    }

    "fail on wrong current version state" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed)
      )
      val versionSeed = PurposeVersionSeed(dailyCalls = 100, Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/waitForApproval",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0021")
    }
  }

}
