package it.pagopa.interop.purposemanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.PurposeApiService
import it.pagopa.interop.purposemanagement.api.impl.ResponseHandlers._
import it.pagopa.interop.purposemanagement.common.system._
import it.pagopa.interop.purposemanagement.error.PurposeManagementErrors._
import it.pagopa.interop.purposemanagement.model._
import it.pagopa.interop.purposemanagement.model.decoupling.{
  DraftPurposeVersionUpdate,
  PurposeUpdate,
  WaitingForApprovalPurposeVersionUpdate
}
import it.pagopa.interop.purposemanagement.model.persistence.Adapters._
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}

import scala.concurrent._

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends PurposeApiService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val settings: ClusterShardingSettings = entity.settings.getOrElse(ClusterShardingSettings(system))

  override def createPurpose(purposeSeed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel =
      s"Adding a purpose for Consumer ${purposeSeed.consumerId} and EService ${purposeSeed.eserviceId}"
    logger.info(operationLabel)

    val purpose: PersistentPurpose = PersistentPurpose.fromSeed(purposeSeed, uuidSupplier, dateTimeSupplier.get())

    val result: Future[Purpose] = commander(purpose.id.toString)
      .askWithStatus(CreatePurpose(purpose, _))
      .map(_.toAPI)

    onComplete(result) { createPurposeResponse[Purpose](operationLabel)(createPurpose200) }
  }

  override def getPurpose(purposeId: String)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Retrieving purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[Purpose] = for {
      maybePurpose <- commander(purposeId).askWithStatus(ref => GetPurpose(purposeId, ref))
      purpose      <- maybePurpose.toFuture(PurposeNotFound(purposeId))
    } yield purpose.toAPI

    onComplete(result) { getPurposeResponse[Purpose](operationLabel)(getPurpose200) }
  }

  override def deletePurpose(
    purposeId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    val operationLabel = s"Deleting purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[Unit] = commander(purposeId).askWithStatus(ref => DeletePurpose(purposeId, ref))

    onComplete(result) { deletePurposeResponse[Unit](operationLabel)(_ => deletePurpose204) }
  }

  override def createPurposeVersion(purposeId: String, purposeVersionSeed: PurposeVersionSeed)(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Adding a version to purpose $purposeId"
    logger.info(operationLabel)

    val purposeVersion: PersistentPurposeVersion =
      PersistentPurposeVersion.fromSeed(purposeVersionSeed, uuidSupplier, dateTimeSupplier)
    val result: Future[PurposeVersion]           =
      commander(purposeId).askWithStatus(ref => CreatePurposeVersion(purposeId, purposeVersion, ref)).map(_.toAPI)

    onComplete(result) { createPurposeVersionResponse[PurposeVersion](operationLabel)(createPurposeVersion200) }
  }

  override def deletePurposeVersion(purposeId: String, versionId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Deleting version $versionId of purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[Unit] = commander(purposeId).askWithStatus(DeletePurposeVersion(purposeId, versionId, _))

    onComplete(result) { deletePurposeVersionResponse[Unit](operationLabel)(_ => deletePurposeVersion204) }
  }

  override def activatePurposeVersion(
    purposeId: String,
    versionId: String,
    activatePurposeVersionPayload: ActivatePurposeVersionPayload
  )(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Activating version $versionId of purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[PurposeVersion] = updateVersionState(
      purposeId,
      versionId,
      ActivatePurposeVersion(
        purposeId,
        versionId,
        activatePurposeVersionPayload.riskAnalysis,
        activatePurposeVersionPayload.stateChangeDetails,
        _
      )
    )

    onComplete(result) { activatePurposeVersionResponse[PurposeVersion](operationLabel)(activatePurposeVersion200) }
  }

  override def suspendPurposeVersion(purposeId: String, versionId: String, stateChangeDetails: StateChangeDetails)(
    implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Suspending version $versionId of purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[PurposeVersion] =
      updateVersionState(purposeId, versionId, SuspendPurposeVersion(purposeId, versionId, stateChangeDetails, _))

    onComplete(result) { suspendPurposeVersionResponse[PurposeVersion](operationLabel)(suspendPurposeVersion200) }
  }

  override def waitForApprovalPurposeVersion(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  )(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Wait for Approval version $versionId of purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[PurposeVersion] = updateVersionState(
      purposeId,
      versionId,
      WaitForApprovalPurposeVersion(purposeId, versionId, stateChangeDetails, _)
    )

    onComplete(result) {
      waitForApprovalPurposeVersionResponse[PurposeVersion](operationLabel)(waitForApprovalPurposeVersion200)
    }
  }

  override def archivePurposeVersion(purposeId: String, versionId: String, stateChangeDetails: StateChangeDetails)(
    implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Archiving version $versionId of purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[PurposeVersion] =
      updateVersionState(purposeId, versionId, ArchivePurposeVersion(purposeId, versionId, stateChangeDetails, _))

    onComplete(result) { archivePurposeVersionResponse[PurposeVersion](operationLabel)(archivePurposeVersion200) }
  }

  override def getPurposes(eServiceId: Option[String], consumerId: Option[String], states: String)(implicit
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Getting purposes for consumer $consumerId, EService $eServiceId, States $states"
    logger.info(operationLabel)

    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, shard.toString)
    )

    val result: Either[Throwable, Purposes] = for {
      statesEnum <- parseArrayParameters(states).traverse(PurposeVersionState.fromValue)
      generator = createListPurposesGenerator(consumerId = consumerId, eServiceId = eServiceId, states = statesEnum)(
        _,
        _
      )
      persistentPurposes = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
      purposes           = persistentPurposes.map(_.toAPI)
    } yield Purposes(purposes = purposes)

    getPurposesResponse[Purposes](operationLabel)(getPurposes200)(result.toTry)

  }

  override def updatePurpose(purposeId: String, payload: PurposeUpdateContent)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Updating Purpose $purposeId"
    logger.info(operationLabel)

    val purposeUpdate = PurposeUpdate.fromApi(uuidSupplier)(payload)

    // When refactoring the application, the update of the purpose and the update of the version
    // should be done with a single command and a single event
    val result: Future[Purpose] = for {
      purpose <- commander(purposeId)
        .askWithStatus(UpdatePurpose(purposeId, purposeUpdate, _))
        .map(_.toAPI)

      draftPurposeVersionUpdate = DraftPurposeVersionUpdate(payload.dailyCalls, dateTimeSupplier.get())
      versionId <- purpose.versions.headOption.toFuture(DraftPurposeVersionNotFound(purposeId)).map(_.id.toString)

      _ <- commander(purposeId)
        .askWithStatus(ref => UpdateDraftPurposeVersion(purposeId, versionId, draftPurposeVersionUpdate, ref))
    } yield purpose

    onComplete(result) { updatePurposeResponse[Purpose](operationLabel)(updatePurpose200) }
  }

  override def updateWaitingForApprovalPurposeVersion(
    purposeId: String,
    versionId: String,
    updateContent: WaitingForApprovalPurposeVersionUpdateContent
  )(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel = s"Updating Waiting For Approval Version $versionId of Purpose $purposeId"
    logger.info(operationLabel)

    val update = WaitingForApprovalPurposeVersionUpdate.fromApi(updateContent, dateTimeSupplier)

    val result: Future[PurposeVersion] =
      commander(purposeId)
        .askWithStatus(ref => UpdateWaitingForApprovalPurposeVersion(purposeId, versionId, update, ref))
        .map(_.toAPI)

    onComplete(result) {
      updateWaitingForApprovalPurposeVersionResponse[PurposeVersion](operationLabel)(
        updateWaitingForApprovalPurposeVersion200
      )
    }
  }

  private def updateVersionState(
    purposeId: String,
    versionId: String,
    command: ActorRef[StatusReply[PersistentPurpose]] => Command
  ): Future[PurposeVersion] = for {
    updatedPurpose <- commander(purposeId).askWithStatus(command)
    version        <- updatedPurpose.versions
      .find(_.id.toString == versionId)
      .toFuture(GenericError(s"Version $versionId of Purpose $purposeId not found after operation"))
  } yield version.toAPI

  private def createListPurposesGenerator(
    consumerId: Option[String],
    eServiceId: Option[String],
    states: List[PurposeVersionState]
  )(from: Int, to: Int): ActorRef[Seq[PersistentPurpose]] => ListPurposes =
    (ref: ActorRef[Seq[PersistentPurpose]]) =>
      ListPurposes(from, to, consumerId, eServiceId, states.map(PersistentPurposeVersionState.fromSeed), ref)

  private def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(PurposePersistentBehavior.TypeKey, getShard(id, settings.numberOfShards))
}
