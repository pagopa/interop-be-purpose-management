package it.pagopa.interop.purposemanagement.provider

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.purposemanagement._
import it.pagopa.interop.purposemanagement.model._

import java.util.UUID
import scala.concurrent.Future

class PurposeSpec extends BaseIntegrationSpec {

  "Creation of a new purpose" must {

    "succeed" in {
      val purposeId        = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        freeOfChargeReason = Some("Reason"),
        dailyCalls = 100
      )

      val response: Future[Purpose] = createPurpose(purposeId, purposeVersionId, purposeSeed)

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq(
          PurposeVersion(
            id = purposeVersionId,
            state = PurposeVersionState.DRAFT,
            createdAt = timestamp,
            dailyCalls = 100
          )
        ),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = purposeSeed.title,
        description = purposeSeed.description,
        riskAnalysisForm = Some(riskAnalysisForm),
        createdAt = timestamp,
        updatedAt = None,
        isFreeOfCharge = true,
        freeOfChargeReason = Some("Reason")
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

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Purpose] =
        for {
          _       <- createPurpose(purposeId, versionId, purposeSeed)
          purpose <- getPurpose(purposeId)
        } yield purpose

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq(
          PurposeVersion(
            id = versionId,
            state = PurposeVersionState.DRAFT,
            createdAt = timestamp,
            expectedApprovalDate = None,
            dailyCalls = 100
          )
        ),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = purposeSeed.title,
        description = purposeSeed.description,
        riskAnalysisForm = Some(riskAnalysisForm),
        createdAt = timestamp,
        updatedAt = None,
        isFreeOfCharge = true
      )

      response.futureValue shouldBe expected
    }

    "fail if purpose does not exist" in {
      val purposeId = UUID.randomUUID()

      val response: Future[Problem] = makeFailingRequest(s"purposes/$purposeId", HttpMethods.GET)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
    }
  }

  "Retrieve of a list of purposes" must {

    "succeed without filters" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val versionId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(
        eserviceId = eServiceId1,
        consumerId = consumerId1,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )
      val purposeSeed2 = PurposeSeed(
        eserviceId = eServiceId2,
        consumerId = consumerId2,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Purposes] =
        for {
          _        <- createPurpose(purposeId1, versionId1, purposeSeed1)
          _        <- createPurpose(purposeId2, versionId2, purposeSeed2)
          response <- getPurposes()
        } yield response

      val expected =
        Seq(
          Purpose(
            id = purposeId1,
            eserviceId = purposeSeed1.eserviceId,
            consumerId = purposeSeed1.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId1,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = None,
            isFreeOfCharge = true
          ),
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId2,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = None,
            isFreeOfCharge = true
          )
        )

      response.futureValue.purposes should contain allElementsOf expected
    }

    "succeed filtering by EService" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val versionId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(
        eserviceId = eServiceId1,
        consumerId = consumerId1,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )
      val purposeSeed2 = PurposeSeed(
        eserviceId = eServiceId2,
        consumerId = consumerId2,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Purposes] =
        for {
          _        <- createPurpose(purposeId1, versionId1, purposeSeed1)
          _        <- createPurpose(purposeId2, versionId2, purposeSeed2)
          response <- getPurposes(eServiceId = Some(eServiceId1))
        } yield response

      val expected =
        Seq(
          Purpose(
            id = purposeId1,
            eserviceId = purposeSeed1.eserviceId,
            consumerId = purposeSeed1.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId1,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = None,
            isFreeOfCharge = true
          )
        )

      response.futureValue.purposes should contain theSameElementsAs expected
    }

    "succeed filtering by Consumer" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val versionId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(
        eserviceId = eServiceId1,
        consumerId = consumerId1,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )
      val purposeSeed2 = PurposeSeed(
        eserviceId = eServiceId2,
        consumerId = consumerId2,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Purposes] =
        for {
          _        <- createPurpose(purposeId1, versionId1, purposeSeed1)
          _        <- createPurpose(purposeId2, versionId2, purposeSeed2)
          response <- getPurposes(consumerId = Some(consumerId2))
        } yield response

      val expected =
        Seq(
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId2,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = None,
            isFreeOfCharge = true
          )
        )

      response.futureValue.purposes should contain theSameElementsAs expected
    }

    "succeed filtering by Version State" in {
      val purposeId1     = UUID.randomUUID()
      val purposeId2     = UUID.randomUUID()
      val purposeId3     = UUID.randomUUID()
      val versionId1_1   = UUID.randomUUID()
      val versionId1_2   = UUID.randomUUID()
      val versionId2_1   = UUID.randomUUID()
      val versionId3_1   = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed1 = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )
      val purposeSeed2 = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )
      val purposeSeed3 = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val versionSeed = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 100)

      val response: Future[Purposes] = for {
        _        <- createPurpose(purposeId1, versionId1_1, purposeSeed1)
        _        <- activateVersion(purposeId1, versionId1_1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        _        <- createPurposeVersion(purposeId1, versionId1_2, versionSeed)
        _        <- createPurpose(purposeId2, versionId2_1, purposeSeed2)
        _        <- activateVersion(purposeId2, versionId2_1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        _        <- suspendVersion(purposeId2, versionId2_1, ChangedBy.CONSUMER)
        _        <- createPurpose(purposeId3, versionId3_1, purposeSeed3)
        _        <- activateVersion(purposeId3, versionId3_1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
        _        <- archiveVersion(purposeId3, versionId3_1, ChangedBy.CONSUMER)
        response <- getPurposes(
          eServiceId = Some(eServiceId),
          consumerId = Some(consumerId),
          states = Seq(PurposeVersionState.ACTIVE, PurposeVersionState.SUSPENDED)
        )
      } yield response

      val expected =
        Seq(
          Purpose(
            id = purposeId1,
            eserviceId = purposeSeed1.eserviceId,
            consumerId = purposeSeed1.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId1_1,
                state = PurposeVersionState.ACTIVE,
                createdAt = timestamp,
                updatedAt = Some(timestamp),
                firstActivationAt = Some(timestamp),
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc),
                dailyCalls = 100
              ),
              PurposeVersion(
                id = versionId1_2,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc),
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = Some(false),
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = Some(timestamp),
            isFreeOfCharge = true
          ),
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId2_1,
                state = PurposeVersionState.SUSPENDED,
                createdAt = timestamp,
                updatedAt = Some(timestamp),
                firstActivationAt = Some(timestamp),
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc),
                dailyCalls = 100
              )
            ),
            suspendedByConsumer = Some(true),
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            riskAnalysisForm = Some(riskAnalysisForm),
            createdAt = timestamp,
            updatedAt = Some(timestamp),
            isFreeOfCharge = true
          )
        )

      response.futureValue.purposes.map(p =>
        purposesAreTheSame(p, expected.find(_.id == p.id).get)
      ) should not contain false
    }
  }

  "Deletion of a purpose" must {

    "succeed" in {
      val purposeId        = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()

      val purposeSeed = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeVersionId, purposeSeed)
          _      <- deletePurposeVersion(purposeId, purposeVersionId)
          result <- deletePurpose(purposeId)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "fail if purpose contains versions" in {
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
        isFreeOfCharge = true,
        dailyCalls = 100
      )

      val response: Future[Problem] = for {
        _      <- createPurpose(purposeId, versionId, purposeSeed)
        result <- makeFailingRequest(s"purposes/$purposeId", HttpMethods.DELETE)
      } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0007")
    }
  }

  "Update of a purpose" must {

    "succeed if no version exists" in {
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
        dailyCalls = 100
      )

      val updateContent = PurposeUpdateContent(
        title = "Another title",
        description = "Another description",
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        riskAnalysisForm = Some(riskAnalysisFormSeed.copy(version = "2.0")),
        dailyCalls = 100
      )

      val response: Future[Purpose] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- updatePurpose(purposeId, updateContent)
        } yield result

      val updatedPurpose = response.futureValue
      updatedPurpose.title shouldBe updateContent.title
      updatedPurpose.description shouldBe updateContent.description
      updatedPurpose.riskAnalysisForm.get.version shouldBe updateContent.riskAnalysisForm.get.version
    }

    "succeed if only one draft version exists" in {
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
        dailyCalls = 100
      )

      val updateContent = PurposeUpdateContent(
        title = "Another title",
        description = "Another description",
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        riskAnalysisForm = Some(riskAnalysisFormSeed.copy(version = "2.0")),
        dailyCalls = 100
      )

      val response: Future[Purpose] =
        for {
          _      <- createPurpose(purposeId, versionId, purposeSeed)
          result <- updatePurpose(purposeId, updateContent)
        } yield result

      val updatedPurpose = response.futureValue
      updatedPurpose.title shouldBe updateContent.title
      updatedPurpose.description shouldBe updateContent.description
      updatedPurpose.riskAnalysisForm.get.version shouldBe updateContent.riskAnalysisForm.get.version
    }

    "fail if purpose does not exist" in {
      val purposeId = UUID.randomUUID()

      val updateContent = PurposeUpdateContent(
        title = "Another title",
        description = "Another description",
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        riskAnalysisForm = Some(riskAnalysisFormSeed.copy(version = "2.0")),
        dailyCalls = 100
      )

      (() => mockUUIDSupplier.get()).expects().returning(UUID.randomUUID()).once()

      val response: Future[Problem] =
        makeFailingRequest(s"purposes/$purposeId", HttpMethods.POST, updateContent)

      val result = response.futureValue
      result.status shouldBe 404
      result.errors.map(_.code) shouldBe Seq("011-0002")
    }

    "fail if an active version exists" in {
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

      val purposeSeed   = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        dailyCalls = 100
      )
      val updateContent = PurposeUpdateContent(
        title = "Another title",
        description = "Another description",
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        riskAnalysisForm = Some(riskAnalysisFormSeed.copy(version = "2.0")),
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, versionId, purposeSeed)
          _ <- activateVersion(purposeId, versionId, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _ = (() => mockUUIDSupplier.get()).expects().returning(UUID.randomUUID()).once()
          result <- makeFailingRequest(s"purposes/$purposeId", HttpMethods.POST, updateContent)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }

    "fail if other versions exist besides the one in draft" in {
      val purposeId      = UUID.randomUUID()
      val versionId1     = UUID.randomUUID()
      val versionId2     = UUID.randomUUID()
      val riskAnalysisId = UUID.randomUUID()
      val eServiceId     = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()

      val riskAnalysisDoc = PurposeVersionDocument(
        id = riskAnalysisId,
        contentType = "a-content-type",
        path = "a/store/path",
        createdAt = timestamp
      )

      val purposeSeed   = PurposeSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        title = "Purpose",
        description = "Purpose description",
        riskAnalysisForm = Some(riskAnalysisFormSeed),
        isFreeOfCharge = false,
        dailyCalls = 100
      )
      val versionSeed   = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc), dailyCalls = 200)
      val updateContent = PurposeUpdateContent(
        title = "Another title",
        description = "Another description",
        riskAnalysisForm = Some(riskAnalysisFormSeed.copy(version = "2.0")),
        isFreeOfCharge = false,
        freeOfChargeReason = None,
        dailyCalls = 100
      )

      val response: Future[Problem] =
        for {
          _ <- createPurpose(purposeId, versionId1, purposeSeed)
          _ <- activateVersion(purposeId, versionId1, ChangedBy.CONSUMER, Some(riskAnalysisDoc))
          _ <- createPurposeVersion(purposeId, versionId2, versionSeed)
          _ = (() => mockUUIDSupplier.get()).expects().returning(UUID.randomUUID()).once()
          result <- makeFailingRequest(s"purposes/$purposeId", HttpMethods.POST, updateContent)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0004")
    }

  }

  def purposesAreTheSame(a: Purpose, b: Purpose): Boolean =
    a match {
      case Purpose(
            b.`id`,
            b.`eserviceId`,
            b.`consumerId`,
            versions,
            b.`suspendedByConsumer`,
            b.`suspendedByProducer`,
            b.`title`,
            b.`description`,
            b.`riskAnalysisForm`,
            b.`createdAt`,
            b.`updatedAt`,
            b.`isFreeOfCharge`,
            b.`freeOfChargeReason`
          ) =>
        versions.size == b.versions.size && versions.toSet == b.versions.toSet
      case _ => false
    }
}
