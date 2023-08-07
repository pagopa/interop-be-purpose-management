package it.pagopa.interop.purposemanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.purposemanagement.ItSpecData._
import it.pagopa.interop.purposemanagement.model.decoupling.{DraftPurposeVersionUpdate, PurposeUpdate}
import it.pagopa.interop.purposemanagement.model.persistence.Adapters._
import it.pagopa.interop.purposemanagement.model.persistence.JsonFormats._
import it.pagopa.interop.purposemanagement.model.purpose.{Active, Archived, Draft, PersistentPurpose}
import it.pagopa.interop.purposemanagement.model.{ChangedBy, StateChangeDetails}
import it.pagopa.interop.purposemanagement.{ItSpecConfiguration, ItSpecHelper}
import it.pagopa.interop.purposemanagement.ItSpecData

class CqrsProjectionSpec extends ScalaTestWithActorTestKit(ItSpecConfiguration.config) with ItSpecHelper {

  "Projection" should {
    "succeed for event PurposeCreated" in {
      val expected  = createPurpose(persistentPurpose)
      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeUpdated" in {
      val purpose   = createPurpose(persistentPurpose)
      val update    = PurposeUpdate(
        title = "New title",
        description = "new Description",
        isFreeOfCharge = true,
        eserviceId = purpose.eserviceId,
        freeOfChargeReason = Some("Siamo una Pubblica Amministrazione"),
        riskAnalysisForm = Some(persistentRiskAnalysisForm)
      )
      val expected  = updatePurpose(purpose.id, update)
      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionCreated" in {
      val purpose    =
        createPurpose(persistentPurpose.copy(versions = Seq(persistentPurposeVersion.copy(state = Archived))))
      val newVersion = createVersion(purpose.id, persistentPurposeVersion)
      val expected   = purpose.copy(versions = purpose.versions :+ newVersion)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionActivated" in {
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER, timestamp = ItSpecData.timestamp)

      val draftVersion = persistentPurposeVersion.copy(state = Draft)
      val otherVersion = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(draftVersion, otherVersion)))

      val expected = activateVersion(purpose.id, draftVersion.id, Some(persistentDocument.toAPI), stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionSuspended" in {
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER, timestamp = ItSpecData.timestamp)

      val activeVersion = persistentPurposeVersion.copy(state = Active)
      val otherVersion  = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(activeVersion, otherVersion)))

      val expected = suspendVersion(purpose.id, activeVersion.id, stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionWaitedForApproval" in {
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER, timestamp = ItSpecData.timestamp)

      val draftVersion = persistentPurposeVersion.copy(state = Draft)
      val otherVersion = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(draftVersion, otherVersion)))

      val expected = waitForApprovalVersion(purpose.id, draftVersion.id, stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionArchived" in {
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER, timestamp = ItSpecData.timestamp)

      val activeVersion = persistentPurposeVersion.copy(state = Active)
      val otherVersion  = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(activeVersion, otherVersion)))

      val expected = archiveVersion(purpose.id, activeVersion.id, stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionUpdated" in {
      val update = DraftPurposeVersionUpdate(dailyCalls = 1234, timestamp = timestamp.plusDays(10))

      val draftVersion = persistentPurposeVersion.copy(state = Draft)
      val otherVersion = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(draftVersion, otherVersion)))

      val updatedVersion = updateDraftVersion(purpose.id, draftVersion.id, update)

      val expected = purpose.copy(versions = purpose.versions.filter(_.id != draftVersion.id) :+ updatedVersion)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionDeleted" in {
      val draftVersion = persistentPurposeVersion.copy(state = Draft)
      val otherVersion = persistentPurposeVersion.copy(state = Archived)

      val purpose = createPurpose(persistentPurpose.copy(versions = Seq(draftVersion, otherVersion)))

      deleteVersion(purpose.id, draftVersion.id)

      val expected = purpose.copy(versions = purpose.versions.filter(_.id != draftVersion.id))

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeDeleted" in {
      val purpose = createPurpose(persistentPurpose.copy(versions = Nil))

      deletePurpose(purpose.id)

      val persisted = find[PersistentPurpose](purpose.id.toString).futureValue

      persisted shouldBe empty
    }

  }

}
