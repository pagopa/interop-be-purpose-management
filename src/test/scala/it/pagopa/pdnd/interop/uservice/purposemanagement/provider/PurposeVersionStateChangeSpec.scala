package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeVersionStateChangeSpec extends BaseIntegrationSpec {

  "Activation of purpose" must {
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield result

      response.futureValue shouldBe Some("")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          _       <- createPurposeVersion(purposeId, versionId1, versionSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.CONSUMER)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expectedVersions = Seq(
        PurposeVersion(
          id = versionId1,
          state = PurposeVersionState.ARCHIVED,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        ),
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.ACTIVE,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, purposeSeed)
          _       <- createPurposeVersion(purposeId, versionId1, versionSeed)
          _       <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          _       <- suspendVersion(purposeId, versionId1, ChangedBy.CONSUMER)
          _       <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _       <- activateVersion(purposeId, versionId2, ChangedBy.CONSUMER)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expectedVersions = Seq(
        PurposeVersion(
          id = versionId1,
          state = PurposeVersionState.ARCHIVED,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        ),
        PurposeVersion(
          id = versionId2,
          state = PurposeVersionState.ACTIVE,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        )
      )

      response.futureValue.versions should contain theSameElementsAs expectedVersions
    }

    "fail if not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(
        s"purposes/$purposeId/versions/$versionId/activate",
        HttpMethods.POST,
        StateChangeDetails(changedBy = ChangedBy.CONSUMER)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = None)

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
          _ <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
          _ <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/activate",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
        } yield result

      response.futureValue shouldBe Some("")
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

    "fail on missing risk analysis" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = None)

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(
            s"purposes/$purposeId/versions/$versionId/waitForApproval",
            HttpMethods.POST,
            StateChangeDetails(changedBy = ChangedBy.CONSUMER)
          )
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0022")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
          _ <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER)
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