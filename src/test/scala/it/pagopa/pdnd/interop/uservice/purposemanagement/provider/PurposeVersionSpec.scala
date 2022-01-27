package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeVersionSpec extends BaseIntegrationSpec {

  "Creation of a new purpose version" must {

    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          result <- createPurposeVersion(purposeId, versionId, versionSeed)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId,
          state = PurposeVersionState.DRAFT,
          createdAt = timestamp,
          expectedApprovalDate = None
        )

      response.futureValue shouldBe expected
    }

    "succeed with risk analysis" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          result <- createPurposeVersion(purposeId, versionId, versionSeed)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId,
          state = PurposeVersionState.DRAFT,
          createdAt = timestamp,
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        )

      response.futureValue shouldBe expected
    }

    "fail if purpose does not exist" in {
      val purposeId = UUID.randomUUID()
      val versionId = UUID.randomUUID()

      val versionSeed = PurposeVersionSeed()

      (() => mockUUIDSupplier.get).expects().returning(versionId).once()
      (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

      val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0033")
    }

    "fail if a version in Draft already exists for the same purpose" in {
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
          _ = (() => mockUUIDSupplier.get).expects().returning(versionId).once()
          _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
          result <- makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0032")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, purposeSeed)
          _ <- createPurposeVersion(purposeId, versionId, versionSeed)
          _ <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
          _ = (() => mockUUIDSupplier.get).expects().returning(versionId).once()
          _ = (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()
          result <- makeFailingRequest(s"purposes/$purposeId/versions", HttpMethods.POST, versionSeed)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0032")
    }
  }

  "Update of a purpose version" must {

    "succeed" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )
      val updateContent = PurposeVersionUpdateContent(Some(riskAnalysisDoc))

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- updatePurposeVersion(purposeId, versionId, updateContent)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId,
          state = PurposeVersionState.DRAFT,
          createdAt = timestamp,
          updatedAt = Some(timestamp),
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        )

      response.futureValue shouldBe expected
    }

    "fail if version does not exist" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )
      val updateContent = PurposeVersionUpdateContent(Some(riskAnalysisDoc))

      (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

      val response: Future[Problem] =
        makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.POST, updateContent)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0029")
    }

    "fail if version is not draft" in {
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val updatedRiskAnalysisDoc = riskAnalysisDoc.copy(path = "a/different/store/path")
      val updateContent          = PurposeVersionUpdateContent(Some(updatedRiskAnalysisDoc))

      (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.POST, updateContent)
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0030")
    }

  }

  "Deletion of a purpose version" must {

    "succeed if version is in Draft" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- deletePurposeVersion(purposeId, versionId)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "succeed if version is in Waiting for Approval" in {
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
          _      <- waitForApprovalVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- deletePurposeVersion(purposeId, versionId)
        } yield result

      response.futureValue shouldBe Some("")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0035")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          _      <- suspendVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0035")
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          _      <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, versionSeed.riskAnalysis)
          _      <- archiveVersion(purposeId, versionId, ChangedBy.CONSUMER)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0035")
    }
  }

}
