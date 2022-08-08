package it.pagopa.interop.purposemanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.purposemanagement.ItSpecData._
import it.pagopa.interop.purposemanagement.model.decoupling.PurposeUpdate
import it.pagopa.interop.purposemanagement.model.persistence.Adapters._
import it.pagopa.interop.purposemanagement.model.persistence.JsonFormats._
import it.pagopa.interop.purposemanagement.model.purpose.{Archived, PersistentPurpose}
import it.pagopa.interop.purposemanagement.model.{ChangedBy, StateChangeDetails}
import it.pagopa.interop.purposemanagement.{ItSpecConfiguration, ItSpecHelper}

class CqrsProjectionSpec extends ScalaTestWithActorTestKit(ItSpecConfiguration.config) with ItSpecHelper {

  "Projection" should {
    "succeed for event PurposeCreated" in {
      val expected  = createPurpose(persistentPurpose)
      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeUpdated" in {
      val purpose = createPurpose(persistentPurpose)
      val update = PurposeUpdate(title = "New title", description = "new Description", Some(persistentRiskAnalysisForm))
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
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER)
      val purpose            =
        createPurpose(persistentPurpose.copy(versions = Seq(persistentPurposeVersion.copy(state = Archived))))
      val version            = createVersion(purpose.id, persistentPurposeVersion)

      val expected = activateVersion(purpose.id, version.id, Some(persistentDocument.toAPI), stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

    "succeed for event PurposeVersionSuspended" in {
      val stateChangeDetails = StateChangeDetails(changedBy = ChangedBy.PRODUCER)
      val purpose            =
        createPurpose(persistentPurpose.copy(versions = Seq(persistentPurposeVersion.copy(state = Archived))))
      val version            = createVersion(purpose.id, persistentPurposeVersion)
      activateVersion(purpose.id, version.id, Some(persistentDocument.toAPI), stateChangeDetails)

      val expected = suspendVersion(purpose.id, version.id, stateChangeDetails)

      val persisted = findOne[PersistentPurpose](expected.id.toString).futureValue

      comparePurposes(expected, persisted)
    }

  }

}
