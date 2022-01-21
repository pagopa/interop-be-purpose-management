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
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE, riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[PurposeVersion] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          result <- createPurposeVersion(purposeId, versionId, versionSeed)
        } yield result

      val expected =
        PurposeVersion(
          id = versionId,
          state = versionSeed.state,
          createdAt = timestamp,
          expectedApprovalDate = None,
          riskAnalysis = Some(riskAnalysisDoc)
        )

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

  "Update of a purpose version" must {

    "succeed" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.DRAFT)

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
          state = versionSeed.state,
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
      result.errors.map(_.code) shouldBe Seq("011-0014")
    }

    "fail if version is not draft" in {
      val purposeId      = UUID.randomUUID()
      val versionId      = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )
      val updateContent = PurposeVersionUpdateContent(Some(riskAnalysisDoc))

      (() => mockDateTimeSupplier.get).expects().returning(timestamp).once()

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(s"purposes/$purposeId/versions/$versionId", HttpMethods.POST, updateContent)
        } yield result

      val result = response.futureValue
      result.status shouldBe 400
      result.errors.map(_.code) shouldBe Seq("011-0015")
    }

  }

}
