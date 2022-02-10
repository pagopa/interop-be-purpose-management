package it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils
import it.pagopa.pdnd.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.PurposeApiService
import it.pagopa.pdnd.interop.uservice.purposemanagement.common.system._
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.InternalErrors.{
  PurposeHasVersions,
  PurposeNotFound,
  PurposeVersionMissingRiskAnalysis,
  PurposeVersionNotFound,
  PurposeVersionNotInDraft,
  PurposeVersionNotInExpectedState,
  PurposeVersionStateConflict
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.PurposeManagementErrors._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling.PurposeVersionUpdate
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.util.{Failure, Success}

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
) extends PurposeApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  override def createPurpose(purposeSeed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Adding a purpose for consumer {} to e-service {}", purposeSeed.consumerId, purposeSeed.eserviceId)
    val purpose: PersistentPurpose                     = PersistentPurpose.fromSeed(purposeSeed, uuidSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurpose]] = createPurpose(purpose)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurpose201(statusReply.getValue.toAPI)
      case Success(statusReply) =>
        logger.error(
          "Error while adding a purpose for consumer {} to e-service {}",
          purposeSeed.consumerId,
          purposeSeed.eserviceId,
          statusReply.getError
        )
        createPurpose409(problemOf(StatusCodes.Conflict, CreatePurposeConflict))
      case Failure(ex) =>
        logger.error(
          "Error while adding a purpose for consumer {} to e-service {}",
          purposeSeed.consumerId,
          purposeSeed.eserviceId,
          ex
        )
        createPurpose400(problemOf(StatusCodes.BadRequest, CreatePurposeBadRequest))
    }
  }

  override def getPurpose(purposeId: String)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Retrieving purpose {}", purposeId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Option[PersistentPurpose]]] = commander.ask(ref => GetPurpose(purposeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.fold(getPurpose404(problemOf(StatusCodes.NotFound, GetPurposeNotFound)))(purpose =>
          getPurpose200(purpose.toAPI)
        )
      case statusReply if statusReply.isError =>
        logger.error("Error retrieving purpose {}", purposeId, statusReply.getError)
        getPurpose400(problemOf(StatusCodes.BadRequest, GetPurposeBadRequest))
    }
  }

  override def deletePurpose(
    purposeId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Deleting purpose {}", purposeId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Unit]] =
      commander.ask(ref => DeletePurpose(purposeId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        deletePurpose204
      case Success(statusReply) =>
        logger.error("Error while deleting purpose {}", purposeId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            deletePurpose404(problemOf(StatusCodes.NotFound, DeletePurposeNotFound(pId)))
          case PurposeHasVersions(pId) =>
            deletePurpose409(problemOf(StatusCodes.Conflict, DeletePurposeVersionsNotEmpty(pId)))
          case _ =>
            deletePurpose400(problemOf(StatusCodes.BadRequest, DeletePurposeBadRequest(purposeId)))
        }
      case Failure(ex) =>
        logger.error("Error while deleting purpose {}", purposeId, ex)
        deletePurpose400(problemOf(StatusCodes.BadRequest, DeletePurposeBadRequest(purposeId)))
    }
  }

  override def createPurposeVersion(purposeId: String, purposeVersionSeed: PurposeVersionSeed)(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Adding a version to purpose {}", purposeId)
    val purposeVersion: PersistentPurposeVersion =
      PersistentPurposeVersion.fromSeed(purposeVersionSeed, uuidSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurposeVersion]] = createPurposeVersion(purposeId, purposeVersion)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurposeVersion201(statusReply.getValue.toAPI)
      case Success(statusReply) =>
        logger.error("Error while adding a version to purpose {}", purposeId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            createPurposeVersion404(problemOf(StatusCodes.NotFound, CreatePurposeVersionNotFound(pId)))
          case PurposeVersionStateConflict(pId, vId, s) =>
            createPurposeVersion409(problemOf(StatusCodes.Conflict, CreatePurposeVersionStateConflict(pId, vId, s)))
          case _ =>
            createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
        }
      case Failure(ex) =>
        logger.error("Error while adding a version to purpose {}", purposeId, ex)
        createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
    }
  }

  override def deletePurposeVersion(purposeId: String, versionId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Deleting version {} of purpose {}", versionId, purposeId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Unit]] =
      commander.ask(ref => DeletePurposeVersion(purposeId, versionId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        deletePurposeVersion204
      case Success(statusReply) =>
        logger.error("Error while deleting version {} of purpose {}", versionId, purposeId, statusReply.getError)
        statusReply.getError match {
          case PurposeVersionNotFound(pId, vId) =>
            deletePurposeVersion404(problemOf(StatusCodes.NotFound, DeletePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInDraft(pId, vId) =>
            deletePurposeVersion409(problemOf(StatusCodes.Conflict, DeletePurposeVersionNotInDraft(pId, vId)))
          case _ =>
            deletePurposeVersion400(
              problemOf(StatusCodes.BadRequest, DeletePurposeVersionBadRequest(purposeId, versionId))
            )
        }
      case Failure(ex) =>
        logger.error("Error while deleting version {} of purpose {}", versionId, purposeId, ex)
        deletePurposeVersion400(problemOf(StatusCodes.BadRequest, DeletePurposeVersionBadRequest(purposeId, versionId)))
    }
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
    logger.info("Activating purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      activatePurposeVersionById(
        purposeId,
        versionId,
        activatePurposeVersionPayload.riskAnalysis,
        activatePurposeVersionPayload.stateChangeDetails
      )
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => activatePurposeVersion200(version.toAPI)
          case None =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply if statusReply.isError =>
        logger.error("Error activating purpose {} version {}", purposeId, versionId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            activatePurposeVersion404(problemOf(StatusCodes.NotFound, ActivatePurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId) =>
            activatePurposeVersion404(problemOf(StatusCodes.NotFound, ActivatePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            activatePurposeVersion400(problemOf(StatusCodes.BadRequest, ActivatePurposeUnexpectedState(pId, vId, s)))
          case PurposeVersionMissingRiskAnalysis(pId, vId) =>
            activatePurposeVersion400(problemOf(StatusCodes.BadRequest, ActivatePurposeMissingRiskAnalysis(pId, vId)))
          case _ =>
            activatePurposeVersion400(
              problemOf(StatusCodes.BadRequest, ActivatePurposeBadRequest(purposeId, versionId))
            )
        }
    }
  }

  override def suspendPurposeVersion(purposeId: String, versionId: String, stateChangeDetails: StateChangeDetails)(
    implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Suspending purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      suspendPurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => suspendPurposeVersion200(version.toAPI)
          case None =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply if statusReply.isError =>
        logger.error("Error suspending purpose {} version {}", purposeId, versionId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            suspendPurposeVersion404(problemOf(StatusCodes.NotFound, SuspendPurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId) =>
            suspendPurposeVersion404(problemOf(StatusCodes.NotFound, SuspendPurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            suspendPurposeVersion400(problemOf(StatusCodes.BadRequest, SuspendPurposeUnexpectedState(pId, vId, s)))
          case _ =>
            suspendPurposeVersion400(problemOf(StatusCodes.BadRequest, SuspendPurposeBadRequest(purposeId, versionId)))
        }
    }
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
    logger.info("Wait for Approval purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      waitForApprovalPurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => waitForApprovalPurposeVersion200(version.toAPI)
          case None =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply if statusReply.isError =>
        logger.error("Error waiting for approval purpose {} version {}", purposeId, versionId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            waitForApprovalPurposeVersion404(problemOf(StatusCodes.NotFound, WaitForApprovalPurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId) =>
            waitForApprovalPurposeVersion404(
              problemOf(StatusCodes.NotFound, WaitForApprovalPurposeVersionNotFound(pId, vId))
            )
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            waitForApprovalPurposeVersion400(
              problemOf(StatusCodes.BadRequest, WaitForApprovalPurposeUnexpectedState(pId, vId, s))
            )
          case PurposeVersionMissingRiskAnalysis(pId, vId) =>
            waitForApprovalPurposeVersion400(
              problemOf(StatusCodes.BadRequest, WaitForApprovalPurposeMissingRiskAnalysis(pId, vId))
            )
          case _ =>
            waitForApprovalPurposeVersion400(
              problemOf(StatusCodes.BadRequest, WaitForApprovalPurposeBadRequest(purposeId, versionId))
            )
        }
    }
  }

  override def archivePurposeVersion(purposeId: String, versionId: String, stateChangeDetails: StateChangeDetails)(
    implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Archiving purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      archivePurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => archivePurposeVersion200(version.toAPI)
          case None =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply if statusReply.isError =>
        logger.error("Error archiving purpose {} version {}", purposeId, versionId, statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId) =>
            archivePurposeVersion404(problemOf(StatusCodes.NotFound, ArchivePurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId) =>
            archivePurposeVersion404(problemOf(StatusCodes.NotFound, ArchivePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            archivePurposeVersion400(problemOf(StatusCodes.BadRequest, ArchivePurposeUnexpectedState(pId, vId, s)))
          case _ =>
            archivePurposeVersion400(problemOf(StatusCodes.BadRequest, ArchivePurposeBadRequest(purposeId, versionId)))
        }
    }
  }

  override def getPurposes(eserviceId: Option[String], consumerId: Option[String], states: String)(implicit
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting purposes for consumer {} to e-service {} with states {}", consumerId, eserviceId, states)
    val sliceSize = 100

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, shard.toString)
    )

    val result = for {
      statesEnum <- parseArrayParameters(states).traverse(PurposeVersionState.fromValue)
      generator = createListPurposesGenerator(consumerId = consumerId, eserviceId = eserviceId, states = statesEnum)(
        _,
        _
      )
      persistentPurposes = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
      purposes           = persistentPurposes.map(_.toAPI)
    } yield Purposes(purposes = purposes)

    result match {
      case Right(purposes) => getPurposes200(purposes)
      case Left(error) =>
        logger.error(
          "Error while getting purposes for consumer {} to e-service {} with states {}",
          consumerId,
          eserviceId,
          states,
          error
        )
        getPurposes400(problemOf(StatusCodes.BadRequest, GetPurposesBadRequest))
    }

  }

  override def updatePurposeVersion(purposeId: String, versionId: String, updateContent: PurposeVersionUpdateContent)(
    implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Updating version {} of purpose {}", versionId, purposeId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    val update = PurposeVersionUpdate.fromApi(updateContent, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurposeVersion]] =
      commander.ask(ref => UpdatePurposeVersion(purposeId, versionId, update, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        updatePurposeVersion200(statusReply.getValue.toAPI)
      case Success(statusReply) =>
        logger.error("Error while updating version {} of purpose {}", versionId, purposeId, statusReply.getError)
        statusReply.getError match {
          case _: PurposeVersionNotFound =>
            updatePurposeVersion404(problemOf(StatusCodes.NotFound, UpdatePurposeVersionNotFound(purposeId, versionId)))
          case _: PurposeVersionNotInDraft =>
            updatePurposeVersion400(
              problemOf(StatusCodes.BadRequest, UpdatePurposeVersionNotInDraft(purposeId, versionId))
            )
          case _ =>
            updatePurposeVersion400(
              problemOf(StatusCodes.BadRequest, UpdatePurposeVersionBadRequest(purposeId, versionId))
            )
        }
      case Failure(ex) =>
        logger.error("Error while updating version {} of purpose {}", versionId, purposeId, ex)
        updatePurposeVersion400(problemOf(StatusCodes.BadRequest, UpdatePurposeVersionBadRequest(purposeId, versionId)))
    }
  }

  private def createPurpose(purpose: PersistentPurpose): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(
        PurposePersistentBehavior.TypeKey,
        AkkaUtils.getShard(purpose.id.toString, settings.numberOfShards)
      )

    commander.ask(ref => CreatePurpose(purpose, ref))
  }

  private def createPurposeVersion(
    purposeId: String,
    purposeVersion: PersistentPurposeVersion
  ): Future[StatusReply[PersistentPurposeVersion]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => CreatePurposeVersion(purposeId, purposeVersion, ref))
  }

  private def activatePurposeVersionById(
    purposeId: String,
    versionId: String,
    riskAnalysis: Option[PurposeVersionDocument],
    stateChangeDetails: StateChangeDetails
  ): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => ActivatePurposeVersion(purposeId, versionId, riskAnalysis, stateChangeDetails, ref))
  }

  private def suspendPurposeVersionById(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  ): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => SuspendPurposeVersion(purposeId, versionId, stateChangeDetails, ref))
  }

  private def waitForApprovalPurposeVersionById(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  ): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => WaitForApprovalPurposeVersion(purposeId, versionId, stateChangeDetails, ref))
  }

  private def archivePurposeVersionById(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  ): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => ArchivePurposeVersion(purposeId, versionId, stateChangeDetails, ref))
  }

  private def createListPurposesGenerator(
    consumerId: Option[String],
    eserviceId: Option[String],
    states: List[PurposeVersionState]
  )(from: Int, to: Int): ActorRef[Seq[PersistentPurpose]] => ListPurposes =
    (ref: ActorRef[Seq[PersistentPurpose]]) =>
      ListPurposes(from, to, consumerId, eserviceId, states.map(PersistentPurposeVersionState.fromSeed), ref)

}
