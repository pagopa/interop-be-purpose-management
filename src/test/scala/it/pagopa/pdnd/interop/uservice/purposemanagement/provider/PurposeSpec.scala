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

  "Retrieve of a list of purposes" must {

    "succeed without filters" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1)
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2)
      val versionSeed  = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

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
                state = versionSeed.state,
                createdAt = timestamp,
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
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

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1)
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2)
      val versionSeed  = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

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
                state = versionSeed.state,
                createdAt = timestamp,
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain allElementsOf expected
    }

    "succeed filtering by Consumer" in {
      val purposeId1  = UUID.randomUUID()
      val versionId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val consumerId2 = UUID.randomUUID()

      val purposeSeed1 = PurposeSeed(eserviceId = eServiceId1, consumerId = consumerId1)
      val purposeSeed2 = PurposeSeed(eserviceId = eServiceId2, consumerId = consumerId2)
      val versionSeed  = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)

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
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain allElementsOf expected
    }

    "succeed filtering by Version State" in {
      val purposeId1   = UUID.randomUUID()
      val purposeId2   = UUID.randomUUID()
      val purposeId3   = UUID.randomUUID()
      val versionId1_1 = UUID.randomUUID()
      val versionId1_2 = UUID.randomUUID()
      val versionId2_1 = UUID.randomUUID()
      val versionId3_1 = UUID.randomUUID()
      val eServiceId   = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()

      val purposeSeed1   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val purposeSeed2   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val purposeSeed3   = PurposeSeed(eserviceId = eServiceId, consumerId = consumerId)
      val versionSeed1_1 = PurposeVersionSeed(state = PurposeVersionState.ACTIVE)
      val versionSeed1_2 = PurposeVersionSeed(state = PurposeVersionState.DRAFT)
      val versionSeed2_1 = PurposeVersionSeed(state = PurposeVersionState.SUSPENDED)
      val versionSeed3_1 = PurposeVersionSeed(state = PurposeVersionState.ARCHIVED)

      val response: Future[Seq[Purpose]] =
        for {
          _        <- createPurpose(purposeId1, purposeSeed1)
          _        <- createPurposeVersion(purposeId1, versionId1_1, versionSeed1_1)
          _        <- createPurposeVersion(purposeId1, versionId1_2, versionSeed1_2)
          _        <- createPurpose(purposeId2, purposeSeed2)
          _        <- createPurposeVersion(purposeId2, versionId2_1, versionSeed2_1)
          _        <- createPurpose(purposeId3, purposeSeed3)
          _        <- createPurposeVersion(purposeId3, versionId3_1, versionSeed3_1)
          response <- getPurposes(states = Seq(PurposeVersionState.ACTIVE, PurposeVersionState.SUSPENDED))
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
                state = versionSeed1_1.state,
                createdAt = timestamp,
                expectedApprovalDate = None
              ),
              PurposeVersion(
                id = versionId1_2,
                state = versionSeed1_2.state,
                createdAt = timestamp,
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            createdAt = timestamp,
            updatedAt = None
          ),
          Purpose(
            id = purposeId2,
            eserviceId = purposeSeed2.eserviceId,
            consumerId = purposeSeed2.consumerId,
            versions = Seq(
              PurposeVersion(
                id = versionId2_1,
                state = versionSeed2_1.state,
                createdAt = timestamp,
                expectedApprovalDate = None
              )
            ),
            suspendedByConsumer = None,
            suspendedByProducer = None,
            createdAt = timestamp,
            updatedAt = None
          )
        )

      response.futureValue should contain allElementsOf expected
    }
  }

}
