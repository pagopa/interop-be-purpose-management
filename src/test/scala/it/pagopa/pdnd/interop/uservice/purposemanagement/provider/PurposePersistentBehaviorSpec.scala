package it.pagopa.pdnd.interop.uservice.purposemanagement.provider

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.pdnd.interop.uservice.purposemanagement._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.PurposePersistentBehavior.updatePurposeFromState
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

class PurposePersistentBehaviorSpec extends ScalaTestWithActorTestKit(SpecConfiguration.config) with AnyWordSpecLike {

  val versionTemplate: PersistentPurposeVersion = PersistentPurposeVersion(
    id = UUID.randomUUID(),
    state = PersistentPurposeVersionState.Draft,
    expectedApprovalDate = None,
    riskAnalysis = None,
    createdAt = timestamp,
    updatedAt = None
  )
  val purposeTemplate: PersistentPurpose = PersistentPurpose(
    id = UUID.randomUUID(),
    eserviceId = UUID.randomUUID(),
    consumerId = UUID.randomUUID(),
    versions = Seq.empty,
    suspendedByConsumer = None,
    suspendedByProducer = None,
    title = "Some title",
    description = None,
    createdAt = timestamp,
    updatedAt = None
  )

  val newTimestamp: OffsetDateTime = OffsetDateTime.of(2023, 12, 31, 11, 22, 33, 44, ZoneOffset.UTC)

  "Update version from state" must {

    "change state from Draft to Active" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Draft)
      val purpose = purposeTemplate

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Active,
        stateChangeDetails = StateChangeDetails(ChangedBy.CONSUMER)
      )(mockDateTimeSupplier)

      val expectedVersion = version.copy(state = PersistentPurposeVersionState.Active, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(versions = Seq(expectedVersion), suspendedByConsumer = Some(false), updatedAt = Some(newTimestamp))

      result shouldBe expected
    }

    "change state from Active to Suspended by Consumer" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Active)
      val purpose = purposeTemplate

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Suspended,
        stateChangeDetails = StateChangeDetails(ChangedBy.CONSUMER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Suspended, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(true),
          suspendedByProducer = None,
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "change state from Active to Suspended by Producer" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Active)
      val purpose = purposeTemplate

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Suspended,
        stateChangeDetails = StateChangeDetails(ChangedBy.PRODUCER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Suspended, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = None,
          suspendedByProducer = Some(true),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "remain Suspended if it's Suspended by Consumer and Producer requests suspension" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Suspended)
      val purpose = purposeTemplate.copy(suspendedByConsumer = Some(true))

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Suspended,
        stateChangeDetails = StateChangeDetails(ChangedBy.PRODUCER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Suspended, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(true),
          suspendedByProducer = Some(true),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "remain Suspended if it's Suspended by Consumer and Producer requests activation" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Suspended)
      val purpose = purposeTemplate.copy(suspendedByConsumer = Some(true), suspendedByProducer = Some(true))

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Active,
        stateChangeDetails = StateChangeDetails(ChangedBy.PRODUCER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Suspended, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(true),
          suspendedByProducer = Some(false),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "remain Suspended if it's Suspended by Producer and Consumer requests activation" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Suspended)
      val purpose = purposeTemplate.copy(suspendedByConsumer = Some(true), suspendedByProducer = Some(true))

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Active,
        stateChangeDetails = StateChangeDetails(ChangedBy.CONSUMER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Suspended, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(false),
          suspendedByProducer = Some(true),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "change state to Active if it's Suspended by Consumer and Consumer requests activation" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Suspended)
      val purpose = purposeTemplate.copy(suspendedByConsumer = Some(true), suspendedByProducer = Some(false))

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Active,
        stateChangeDetails = StateChangeDetails(ChangedBy.CONSUMER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Active, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(false),
          suspendedByProducer = Some(false),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }

    "change state to Active if it's Suspended by Producer and Producer requests activation" in {
      val version = versionTemplate.copy(state = PersistentPurposeVersionState.Suspended)
      val purpose = purposeTemplate.copy(suspendedByConsumer = Some(false), suspendedByProducer = Some(true))

      (() => mockDateTimeSupplier.get).expects().returning(newTimestamp).once()

      val result = updatePurposeFromState(
        purpose,
        version,
        newVersionState = PersistentPurposeVersionState.Active,
        stateChangeDetails = StateChangeDetails(ChangedBy.PRODUCER)
      )(mockDateTimeSupplier)

      val expectedVersion =
        version.copy(state = PersistentPurposeVersionState.Active, updatedAt = Some(newTimestamp))
      val expected =
        purpose.copy(
          versions = Seq(expectedVersion),
          suspendedByConsumer = Some(false),
          suspendedByProducer = Some(false),
          updatedAt = Some(newTimestamp)
        )

      result shouldBe expected
    }
  }

}
