package it.pagopa.interop.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.purposemanagement._
import it.pagopa.interop.purposemanagement.model.ChangedBy.CONSUMER
import it.pagopa.interop.purposemanagement.model._

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

class PurposeVersionSpec extends BaseIntegrationSpec {

  "Creation of a new purpose version" must {

    "succeed" in {
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

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, versionId1, purposeSeed)
          _      <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _      <- suspendVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          result <- createPurposeVersion(purposeId, versionId2, versionSeed)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.DRAFT,
          createdAt = timestamp,
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

      response.futureValue shouldBe expected
    }

    "succeed with risk analysis" in {
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

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, versionId1, purposeSeed)
          _      <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _      <- suspendVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          result <- createPurposeVersion(purposeId, versionId2, versionSeed)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.DRAFT,
          createdAt = timestamp,
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

      response.futureValue shouldBe expected
    }

    "fail if purpose does not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val versionSeed = PurposeVersionSeed(dailyCalls = 100)

      (() => mockUUIDSupplier.get()).expects().returning(versionId).once()
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
    }

    "fail if a version in Draft already exists for the same purpose" in {
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
      val versionSeed = PurposeVersionSeed(dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, versionId, purposeSeed)
          _ = (() => mockUUIDSupplier.get()).expects().returning(versionId).once()
          _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
          result <- makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0005")
    }

    "fail if a version in Waiting For Approval already exists for the same purpose" in {
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
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, versionId, purposeSeed)
          _ <- waitForApprovalVersion(purposeId, versionId, CONSUMER)
          _ = (() => mockUUIDSupplier.get()).expects().returning(versionId).once()
          _ = (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()
          result <- makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0005")
    }
  }

  "Update of a purpose version in waiting for approval" must {
    "succeed" in {
      val purposeId        = UUID.randomUUID()
      val versionId        = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()
      val approvalDateTime = OffsetDateTime.now()

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

      val updateContent = WaitingForApprovalPurposeVersionUpdateContent(expectedApprovalDate = approvalDateTime)

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          _      <- waitForApprovalVersion(purposeId, versionId, ChangedBy.PRODUCER)
          result <- updateWaitingForApprovalPurposeVersion(purposeId, versionId, updateContent)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId,
          state = PurposeVersionState.WAITING_FOR_APPROVAL,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          dailyCalls = 100,
          firstActivationAt = Some(timestamp),
          expectedApprovalDate = Some(approvalDateTime),
          riskAnalysis = None
        )

      response.futureValue shouldBe expected
    }

    "fail if version does not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val updateContent = WaitingForApprovalPurposeVersionUpdateContent(expectedApprovalDate = timestamp)

      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val response: Future[Problem] =
        makeFailingRequest(
          s"purposes/$purposeId/versions/$versionId/update/waitingForApproval",
          HttpMethods.POST,
          updateContent
        )

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0006")
    }
    "fail if version is not in waiting for approval" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

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
        dailyCalls = 200
      )

      val updateContent = WaitingForApprovalPurposeVersionUpdateContent(expectedApprovalDate = timestamp)

      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/update/waitingForApproval",
            HttpMethods.POST,
            updateContent
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }
  }

  "Deletion of a purpose version" must {

    "succeed if version is in Draft" in {
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

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- deletePurposeVersion(purposeId, versionId)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "succeed if version is in Waiting for Approval" in {
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

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          _      <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- deletePurposeVersion(purposeId, versionId)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "succeed if version is in Waiting for Approval and previous version was Suspended" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val versionWFAId   = UUID.randomUUID()
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
          _              <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
          _              <- createPurposeVersion(purposeId, versionWFAId, versionSeed)
          _              <- waitForApprovalVersion(purposeId, versionWFAId, ChangedBy.CONSUMER)
          _              <- deletePurposeVersion(purposeId, versionWFAId)
          updatedPurpose <- getPurpose(purposeId)
        } yield updatedPurpose

      val purpose = response.futureValue

      purpose.suspendedByConsumer shouldBe Some(true)
      purpose.suspendedByProducer shouldBe None
      purpose.versions.map(v => (v.id, v.state)) shouldBe Seq((versionId, PurposeVersionState.SUSPENDED))

    }

    "fail if the version is in Active" in {
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
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }

    "fail if the version is in Suspended" in {
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
          _      <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }

    "fail if the version is in Archived" in {
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
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }
  }

}
