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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        } yield result

      val version = response.futureValue

      val expected =
        PurposeVersion(
          id = versionId,
          state = PurposeVersionState.ACTIVE,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          firstActivationAt = Some(timestamp),
          dailyCalls = 100,
          riskAnalysis = Some(
            PurposeVersionDocument(
              id = riskAnalysisId,
              contentType = "a-content-type",
              path = "a/store/path",
              createdAt = timestamp
            )
          )
        )

      version shouldBe expected
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          purpose <- createPurpose(purposeId, versionId, purposeSeed)
          result  <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        } yield (purpose.versions.head, result)

      val (version, result) = response.futureValue

      val expected = version.copy(
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, versionId1, purposeSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, versionId1, purposeSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
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

    "succeed, archive the old suspended version and change to active when it comes together with a waiting-for-approval version" in {
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, versionId1, purposeSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- waitForApprovalVersion(purposeId, versionId2, ChangedBy.CONSUMER)
          _       <- suspendVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.PRODUCER, versionSeed.riskAnalysis)
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

      val result: Purpose = response.futureValue
      result.suspendedByConsumer shouldBe Some(false)
      result.suspendedByProducer shouldBe Some(false)
      result.versions should contain theSameElementsAs expectedVersions
    }

    "succeed, archive the old active version and keep it active when it comes together with a waiting-for-approval version" in {
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, versionId1, purposeSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- waitForApprovalVersion(purposeId, versionId2, ChangedBy.CONSUMER)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.PRODUCER, versionSeed.riskAnalysis)
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          purpose <- createPurpose(purposeId, versionId, purposeSeed)
          _       <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc), firstActivationAt)
          _       <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result  <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, None, secondActivationAt)
        } yield (purpose.versions.head, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ACTIVE,
        updatedAt = Some(secondActivationAt),
        firstActivationAt = Some(firstActivationAt),
        riskAnalysis = Some(
          PurposeVersionDocument(
            id = riskAnalysisId,
            contentType = "a-content-type",
            path = "a/store/path",
            createdAt = timestamp
          )
        )
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/activate",
        HttpMethods.POST,
        ActivatePurposeVersionPayload(stateChangeDetails =
          StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
        )
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            ActivatePurposeVersionPayload(stateChangeDetails =
              StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
            )
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0003")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _      <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            ActivatePurposeVersionPayload(stateChangeDetails =
              StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
            )
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0004")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          purpose <- createPurpose(purposeId, versionId, purposeSeed)
          _       <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          result  <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (purpose.versions.head, result)

      val (version, result) = response.futureValue

      val expected = version.copy(
        state = PurposeVersionState.SUSPENDED,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp),
        riskAnalysis = Some(
          PurposeVersionDocument(
            id = riskAnalysisId,
            contentType = "a-content-type",
            path = "a/store/path",
            createdAt = timestamp
          )
        )
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/suspend",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/suspend",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0004")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          purpose <- createPurpose(purposeId, versionId, purposeSeed)
          _       <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          result  <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (purpose.versions.head, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.ARCHIVED,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp),
        riskAnalysis = Some(
          PurposeVersionDocument(
            id = riskAnalysisId,
            contentType = "a-content-type",
            path = "a/store/path",
            createdAt = timestamp
          )
        )
      )
      result shouldBe expected
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/archive",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/archive",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0004")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[(PurposeVersion, PurposeVersion)] =
        for {
          purpose <- createPurpose(purposeId, versionId, purposeSeed)
          result  <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield (purpose.versions.head, result)

      val (version, result) = response.futureValue
      val expected          = version.copy(
        state = PurposeVersionState.WAITING_FOR_APPROVAL,
        updatedAt = Some(timestamp),
        firstActivationAt = Some(timestamp)
      )
      result shouldBe expected
    }

    "succeed and stay suspended by Producer if previous version was suspended" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val wfaVersionId   = UUID.randomUUID()
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purpose] =
        for {
          _              <- createPurpose(purposeId, versionId, purposeSeed)
          _              <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _              <- suspendVersion(purposeId, versionId, ChangedBy.PRODUCER)
          _              <- createPurposeVersion(purposeId, wfaVersionId, versionSeed)
          _              <- waitForApprovalVersion(purposeId, wfaVersionId, ChangedBy.CONSUMER)
          updatedPurpose <- getPurpose(purposeId)
        } yield updatedPurpose

      val updatedPurpose = response.futureValue

      updatedPurpose.suspendedByConsumer.getOrElse(false) shouldBe false
      updatedPurpose.suspendedByProducer shouldBe Some(true)
      updatedPurpose.versions.map(v => (v.id, v.state)) shouldBe Seq(
        (versionId, PurposeVersionState.SUSPENDED),
        (wfaVersionId, PurposeVersionState.WAITING_FOR_APPROVAL)
      )
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/waitForApproval",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
      )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
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
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/waitForApproval",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER, timestamp = timestamp)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }
  }
}
