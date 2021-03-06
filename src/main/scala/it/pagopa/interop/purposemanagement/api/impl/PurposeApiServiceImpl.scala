package it.pagopa.interop.purposemanagement.api.impl

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
import it.pagopa.interop.commons.jwt.{
  ADMIN_ROLE,
  API_ROLE,
  INTERNAL_ROLE,
  M2M_ROLE,
  SECURITY_ROLE,
  authorizeInterop,
  hasPermissions
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.purposemanagement.api.PurposeApiService
import it.pagopa.interop.purposemanagement.common.system._
import it.pagopa.interop.purposemanagement.error.InternalErrors
import it.pagopa.interop.purposemanagement.error.InternalErrors._
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
import scala.util.{Failure, Success}

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier,
  dateTimeSupplier: OffsetDateTimeSupplier
) extends PurposeApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  private[this] def authorize(roles: String*)(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(hasPermissions(roles: _*), problemOf(StatusCodes.Forbidden, OperationForbidden)) {
      route
    }

  override def createPurpose(purposeSeed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Adding a purpose for consumer {} to e-service {}", purposeSeed.consumerId, purposeSeed.eserviceId)
    val purpose: PersistentPurpose = PersistentPurpose.fromSeed(purposeSeed, uuidSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurpose]] = createPurpose(purpose)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurpose201(statusReply.getValue.toAPI)
      case Success(statusReply)                          =>
        logger.error(
          s"Error while adding a purpose for consumer ${purposeSeed.consumerId} to e-service ${purposeSeed.eserviceId}",
          statusReply.getError
        )
        createPurpose409(problemOf(StatusCodes.Conflict, CreatePurposeConflict))
      case Failure(ex)                                   =>
        logger.error(
          s"Error while adding a purpose for consumer ${purposeSeed.consumerId} to e-service ${purposeSeed.eserviceId}",
          ex
        )
        createPurpose400(problemOf(StatusCodes.BadRequest, CreatePurposeBadRequest))
    }
  }

  override def getPurpose(purposeId: String)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, SECURITY_ROLE, API_ROLE, M2M_ROLE, INTERNAL_ROLE) {
    logger.info("Retrieving purpose {}", purposeId)
    val commander: EntityRef[Command]                          =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Option[PersistentPurpose]]] = commander.ask(ref => GetPurpose(purposeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.fold(getPurpose404(problemOf(StatusCodes.NotFound, GetPurposeNotFound)))(purpose =>
          getPurpose200(purpose.toAPI)
        )
      case statusReply                          =>
        logger.error(s"Error retrieving purpose ${purposeId}", statusReply.getError)
        getPurpose400(problemOf(StatusCodes.BadRequest, GetPurposeBadRequest))
    }
  }

  override def deletePurpose(
    purposeId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Deleting purpose {}", purposeId)
      val commander: EntityRef[Command]     =
        sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
      val result: Future[StatusReply[Unit]] =
        commander.ask(ref => DeletePurpose(purposeId, ref))

      onComplete(result) {
        case Success(statusReply) if statusReply.isSuccess =>
          deletePurpose204
        case Success(statusReply)                          =>
          logger.error(s"Error while deleting purpose ${purposeId}", statusReply.getError)
          statusReply.getError match {
            case PurposeNotFound(pId)    =>
              deletePurpose404(problemOf(StatusCodes.NotFound, DeletePurposeNotFound(pId)))
            case PurposeHasVersions(pId) =>
              deletePurpose409(problemOf(StatusCodes.Conflict, DeletePurposeVersionsNotEmpty(pId)))
            case _                       =>
              deletePurpose400(problemOf(StatusCodes.BadRequest, DeletePurposeBadRequest(purposeId)))
          }
        case Failure(ex)                                   =>
          logger.error(s"Error while deleting purpose ${purposeId}", ex)
          deletePurpose400(problemOf(StatusCodes.BadRequest, DeletePurposeBadRequest(purposeId)))
      }
    }

  override def createPurposeVersion(purposeId: String, purposeVersionSeed: PurposeVersionSeed)(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Adding a version to purpose {}", purposeId)
    val purposeVersion: PersistentPurposeVersion              =
      PersistentPurposeVersion.fromSeed(purposeVersionSeed, uuidSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurposeVersion]] = createPurposeVersion(purposeId, purposeVersion)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurposeVersion201(statusReply.getValue.toAPI)
      case Success(statusReply)                          =>
        logger.error(s"Error while adding a version to purpose ${purposeId}", statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId)                     =>
            createPurposeVersion404(problemOf(StatusCodes.NotFound, CreatePurposeVersionNotFound(pId)))
          case PurposeVersionStateConflict(pId, vId, s) =>
            createPurposeVersion409(problemOf(StatusCodes.Conflict, CreatePurposeVersionStateConflict(pId, vId, s)))
          case _                                        =>
            createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while adding a version to purpose ${purposeId}", ex)
        createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
    }
  }

  override def deletePurposeVersion(purposeId: String, versionId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Deleting version {} of purpose {}", versionId, purposeId)
    val commander: EntityRef[Command]     =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Unit]] =
      commander.ask(ref => DeletePurposeVersion(purposeId, versionId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        deletePurposeVersion204
      case Success(statusReply)                          =>
        logger.error(s"Error while deleting version ${versionId} of purpose ${purposeId}", statusReply.getError)
        statusReply.getError match {
          case PurposeVersionNotFound(pId, vId)            =>
            deletePurposeVersion404(problemOf(StatusCodes.NotFound, DeletePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInDeletableState(pId, vId) =>
            deletePurposeVersion403(problemOf(StatusCodes.Conflict, DeletePurposeVersionNotInDeletableState(pId, vId)))
          case _                                           =>
            deletePurposeVersion400(
              problemOf(StatusCodes.BadRequest, DeletePurposeVersionBadRequest(purposeId, versionId))
            )
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while deleting version ${versionId} of purpose ${purposeId}", ex)
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
  ): Route = authorize(ADMIN_ROLE) {
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
          case None          =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply                          =>
        logger.error(s"Error activating purpose ${purposeId} version ${versionId}", statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId)                          =>
            activatePurposeVersion404(problemOf(StatusCodes.NotFound, ActivatePurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId)              =>
            activatePurposeVersion404(problemOf(StatusCodes.NotFound, ActivatePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            activatePurposeVersion400(problemOf(StatusCodes.BadRequest, ActivatePurposeUnexpectedState(pId, vId, s)))
          case PurposeVersionMissingRiskAnalysis(pId, vId)   =>
            activatePurposeVersion400(problemOf(StatusCodes.BadRequest, ActivatePurposeMissingRiskAnalysis(pId, vId)))
          case _                                             =>
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Suspending purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      suspendPurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => suspendPurposeVersion200(version.toAPI)
          case None          =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply                          =>
        logger.error(s"Error suspending purpose ${purposeId} version ${versionId}", statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId)                          =>
            suspendPurposeVersion404(problemOf(StatusCodes.NotFound, SuspendPurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId)              =>
            suspendPurposeVersion404(problemOf(StatusCodes.NotFound, SuspendPurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            suspendPurposeVersion400(problemOf(StatusCodes.BadRequest, SuspendPurposeUnexpectedState(pId, vId, s)))
          case _                                             =>
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Wait for Approval purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      waitForApprovalPurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => waitForApprovalPurposeVersion200(version.toAPI)
          case None          =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply                          =>
        logger.error(s"Error waiting for approval purpose ${purposeId} version ${versionId}", statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId)                          =>
            waitForApprovalPurposeVersion404(problemOf(StatusCodes.NotFound, WaitForApprovalPurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId)              =>
            waitForApprovalPurposeVersion404(
              problemOf(StatusCodes.NotFound, WaitForApprovalPurposeVersionNotFound(pId, vId))
            )
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            waitForApprovalPurposeVersion400(
              problemOf(StatusCodes.BadRequest, WaitForApprovalPurposeUnexpectedState(pId, vId, s))
            )
          case PurposeVersionMissingRiskAnalysis(pId, vId)   =>
            waitForApprovalPurposeVersion400(
              problemOf(StatusCodes.BadRequest, WaitForApprovalPurposeMissingRiskAnalysis(pId, vId))
            )
          case _                                             =>
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Archiving purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      archivePurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.versions.find(_.id.toString == versionId) match {
          case Some(version) => archivePurposeVersion200(version.toAPI)
          case None          =>
            val problem = problemOf(StatusCodes.InternalServerError, UnexpectedMissingVersion(purposeId, versionId))
            complete(problem.status, problem)
        }
      case statusReply                          =>
        logger.error(s"Error archiving purpose ${purposeId} version ${versionId}", statusReply.getError)
        statusReply.getError match {
          case PurposeNotFound(pId)                          =>
            archivePurposeVersion404(problemOf(StatusCodes.NotFound, ArchivePurposeNotFound(pId)))
          case PurposeVersionNotFound(pId, vId)              =>
            archivePurposeVersion404(problemOf(StatusCodes.NotFound, ArchivePurposeVersionNotFound(pId, vId)))
          case PurposeVersionNotInExpectedState(pId, vId, s) =>
            archivePurposeVersion400(problemOf(StatusCodes.BadRequest, ArchivePurposeUnexpectedState(pId, vId, s)))
          case _                                             =>
            archivePurposeVersion400(problemOf(StatusCodes.BadRequest, ArchivePurposeBadRequest(purposeId, versionId)))
        }
    }
  }

  override def getPurposes(eserviceId: Option[String], consumerId: Option[String], states: String)(implicit
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, SECURITY_ROLE, API_ROLE, M2M_ROLE) {
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
      case Left(ex)        =>
        logger.error(
          s"Error while getting purposes for consumer ${consumerId} to e-service ${eserviceId} with states ${states}",
          ex
        )
        getPurposes400(problemOf(StatusCodes.BadRequest, GetPurposesBadRequest))
    }

  }

  override def updatePurpose(purposeId: String, payload: PurposeUpdateContent)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Updating Purpose {}", purposeId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    val update                                         = PurposeUpdate.fromApi(uuidSupplier)(payload)
    val result: Future[StatusReply[PersistentPurpose]] =
      commander.ask(ref => UpdatePurpose(purposeId, update, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        updatePurpose200(statusReply.getValue.toAPI)
      case Success(statusReply)                          =>
        logger.error(s"Error updating Purpose ${purposeId}", statusReply.getError)
        statusReply.getError match {
          case _: PurposeNotFound          =>
            updatePurpose404(problemOf(StatusCodes.NotFound, UpdatePurposeNotFound(purposeId)))
          case _: PurposeVersionNotInDraft =>
            updatePurpose403(problemOf(StatusCodes.Forbidden, UpdatePurposeNotInDraft(purposeId)))
          case _                           =>
            val problem = problemOf(StatusCodes.InternalServerError, UpdatePurposeError(purposeId))
            complete(problem.status, problem)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error updating Purpose ${purposeId}", ex)
        val problem = problemOf(StatusCodes.InternalServerError, UpdatePurposeError(purposeId))
        complete(problem.status, problem)
    }
  }

  override def updateDraftPurposeVersion(
    purposeId: String,
    versionId: String,
    updateContent: DraftPurposeVersionUpdateContent
  )(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Updating version {} of purpose {}", versionId, purposeId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    val update = DraftPurposeVersionUpdate.fromApi(updateContent, dateTimeSupplier)

    val result: Future[StatusReply[PersistentPurposeVersion]] =
      commander.ask(ref => UpdateDraftPurposeVersion(purposeId, versionId, update, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        updateDraftPurposeVersion200(statusReply.getValue.toAPI)
      case Success(statusReply)                          =>
        logger.error(s"Error while updating version ${versionId} of purpose ${purposeId}", statusReply.getError)
        statusReply.getError match {
          case _: PurposeVersionNotFound   =>
            updateDraftPurposeVersion404(
              problemOf(StatusCodes.NotFound, UpdatePurposeVersionNotFound(purposeId, versionId))
            )
          case _: PurposeVersionNotInDraft =>
            updateDraftPurposeVersion403(
              problemOf(StatusCodes.BadRequest, UpdatePurposeVersionNotInDraft(purposeId, versionId))
            )
          case _                           =>
            complete(
              StatusCodes.InternalServerError,
              problemOf(StatusCodes.InternalServerError, UpdatePurposeVersionBadRequest(purposeId, versionId))
            )
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while updating version ${versionId} of purpose ${purposeId}", ex)
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, UpdatePurposeVersionBadRequest(purposeId, versionId))
        )
    }
  }

  override def updateWaitingForApprovalPurposeVersion(
    purposeId: String,
    versionId: String,
    updateContent: WaitingForApprovalPurposeVersionUpdateContent
  )(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Updating version {} of purpose {}", versionId, purposeId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    val update = WaitingForApprovalPurposeVersionUpdate.fromApi(updateContent, dateTimeSupplier)

    val result: Future[StatusReply[PersistentPurposeVersion]] =
      commander.ask(ref => UpdateWaitingForApprovalPurposeVersion(purposeId, versionId, update, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        updateDraftPurposeVersion200(statusReply.getValue.toAPI)
      case Success(statusReply)                          =>
        logger.error(s"Error while updating version ${versionId} of purpose ${purposeId}", statusReply.getError)
        statusReply.getError match {
          case _: PurposeVersionNotFound                               =>
            updateDraftPurposeVersion404(
              problemOf(StatusCodes.NotFound, UpdatePurposeVersionNotFound(purposeId, versionId))
            )
          case _: InternalErrors.PurposeVersionNotInWaitingForApproval =>
            updateDraftPurposeVersion403(
              problemOf(StatusCodes.BadRequest, UpdatePurposeVersionNotInWaitingForApproval(purposeId, versionId))
            )
          case _                                                       =>
            complete(
              StatusCodes.InternalServerError,
              problemOf(StatusCodes.InternalServerError, UpdatePurposeVersionBadRequest(purposeId, versionId))
            )
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while updating version ${versionId} of purpose ${purposeId}", ex)
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, UpdatePurposeVersionBadRequest(purposeId, versionId))
        )
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
