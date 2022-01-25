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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")

      val response: Future[Purpose] = createPurpose(purposeId, purposeSeed)

      val expected = Purpose(
        id = purposeId,
        eserviceId = purposeSeed.eserviceId,
        consumerId = purposeSeed.consumerId,
        versions = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = purposeSeed.title,
        description = purposeSeed.description,
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

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

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
          PurposeVersion(
            id = versionId,
            state = PurposeVersionState.DRAFT,
            createdAt = timestamp,
            expectedApprovalDate = None
          )
        ),
        suspendedByConsumer = None,
        suspendedByProducer = None,
        title = purposeSeed.title,
        description = purposeSeed.description,
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

  "Retrieve of a list of purposes" must {

    "succeed without filters" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1, title = "Purpose")
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2, title = "Purpose")
      val versionSeed  = PurposeVersionSeed()

      val response: Future[Seq[Purpose]] =
        for {
          _        <- createPurpose(purposeId1, purposeSeed1)
          _        <- createPurposeVersion(purposeId1, versionId1, versionSeed)
          _        <- createPurpose(purposeId2, purposeSeed2)
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
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            createdAt = timestamp,
            updatedAt = None
          ),
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq.empty,
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain allElementsOf expected
    }

    "succeed filtering by EService" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1, title = "Purpose")
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2, title = "Purpose")
      val versionSeed  = PurposeVersionSeed()

      val response: Future[Seq[Purpose]] =
        for {
          _        <- createPurpose(purposeId1, purposeSeed1)
          _        <- createPurposeVersion(purposeId1, versionId1, versionSeed)
          _        <- createPurpose(purposeId2, purposeSeed2)
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
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain theSameElementsAs expected
    }

    "succeed filtering by Consumer" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1, title = "Purpose")
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2, title = "Purpose")
      val versionSeed  = PurposeVersionSeed()

      val response: Future[Seq[Purpose]] =
        for {
          _        <- createPurpose(purposeId1, purposeSeed1)
          _        <- createPurposeVersion(purposeId1, versionId1, versionSeed)
          _        <- createPurpose(purposeId2, purposeSeed2)
          response <- getPurposes(consumerId = Some(consumerId2))
        } yield response

      val expected =
        Seq(
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq.empty,
            suspendedByConsumer = None,
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain theSameElementsAs expected
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

      val purposeSeed1   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val purposeSeed2   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val purposeSeed3   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed1_1 = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))
      val versionSeed1_2 = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))
      val versionSeed2_1 = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))
      val versionSeed3_1 = PurposeVersionSeed(riskAnalysis = Some(riskAnalysisDoc))

      val response: Future[Seq[Purpose]] =
        for {
          _ <- createPurpose(purposeId1, purposeSeed1)
          _ <- createPurposeVersion(purposeId1, versionId1_1, versionSeed1_1)
          _ <- activateVersion(purposeId1, versionId1_1, ChangedBy.CONSUMER)
          _ <- createPurposeVersion(purposeId1, versionId1_2, versionSeed1_2)
          _ <- createPurpose(purposeId2, purposeSeed2)
          _ <- createPurposeVersion(purposeId2, versionId2_1, versionSeed2_1)
          _ <- activateVersion(purposeId2, versionId2_1, ChangedBy.CONSUMER)
          _ <- suspendVersion(purposeId2, versionId2_1, ChangedBy.CONSUMER)
          _ <- createPurpose(purposeId3, purposeSeed3)
          _ <- createPurposeVersion(purposeId3, versionId3_1, versionSeed3_1)
          _ <- activateVersion(purposeId3, versionId3_1, ChangedBy.CONSUMER)
          _ <- archiveVersion(purposeId3, versionId3_1, ChangedBy.CONSUMER)
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
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc)
              ),
              PurposeVersion(
                id = versionId1_2,
                state = PurposeVersionState.DRAFT,
                createdAt = timestamp,
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc)
              )
            ),
            suspendedByConsumer = Some(false),
            suspendedByProducer = None,
            title = purposeSeed1.title,
            description = purposeSeed1.description,
            createdAt = timestamp,
            updatedAt = Some(timestamp)
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
                expectedApprovalDate = None,
                riskAnalysis = Some(riskAnalysisDoc)
              )
            ),
            suspendedByConsumer = Some(true),
            suspendedByProducer = None,
            title = purposeSeed2.title,
            description = purposeSeed2.description,
            createdAt = timestamp,
            updatedAt = Some(timestamp)
          )
        )

      response.futureValue.map(p => purposesAreTheSame(p, expected.find(_.id == p.id).get)) should not contain false
    }
  }

  "Deletion of a purpose" must {

    "succeed" in {
      val purposeId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")

      val response: Future[Option[String]] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          result <- deletePurpose(purposeId)
        } yield result

      response.futureValue shouldBe Some("")
    }

    "fail if purpose contains versions" in {
      val purposeId  = UUID.randomUUID()
      val versionId  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val purposeSeed = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId, title = "Purpose")
      val versionSeed = PurposeVersionSeed()

      val response: Future[Problem] =
        for {
          _      <- createPurpose(purposeId, purposeSeed)
          _      <- createPurposeVersion(purposeId, versionId, versionSeed)
          result <- makeFailingRequest(s"purposes/$purposeId", HttpMethods.DELETE)
        } yield result

      val result = response.futureValue
      result.status shouldBe 409
      result.errors.map(_.code) shouldBe Seq("011-0039")
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
            b.`createdAt`,
            b.`updatedAt`
          ) =>
        versions.size == b.versions.size && versions.toSet == b.versions.toSet
      case _ => false
    }
}
