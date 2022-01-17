package it.pagopa.pdnd.interop.uservice.purposemanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils
import it.pagopa.pdnd.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.api.PurposeApiService
import it.pagopa.pdnd.interop.uservice.purposemanagement.common.system._
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.PurposeManagementErrors._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.service.OffsetDateTimeSupplier
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.util.{Failure, Success}

class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  UUIDSupplier: UUIDSupplier,
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
    val purpose: PersistentPurpose                     = PersistentPurpose.fromAPI(purposeSeed, UUIDSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurpose]] = createPurpose(purpose)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurpose200(PersistentPurpose.toAPI(statusReply.getValue))
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
    logger.info("Getting purpose {}", purposeId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Option[PersistentPurpose]]] = commander.ask(ref => GetPurpose(purposeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.fold(getPurpose404(problemOf(StatusCodes.NotFound, GetPurposeNotFound)))(purpose =>
          getPurpose200(PersistentPurpose.toAPI(purpose))
        )
      case statusReply if statusReply.isError =>
        logger.error("Error in getting purpose {}", purposeId, statusReply.getError)
        getPurpose400(problemOf(StatusCodes.BadRequest, GetPurposeBadRequest))
    }
  }

  override def createPurposeVersion(purposeId: String, purposeVersionSeed: PurposeVersionSeed)(implicit
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersion],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    // TODO It could return 409 if state conflicts
    logger.info("Adding a version to purpose", purposeId)
    val purposeVersion: PersistentPurposeVersion =
      PersistentPurposeVersion.fromAPI(purposeVersionSeed, UUIDSupplier, dateTimeSupplier)
    val result: Future[StatusReply[PersistentPurposeVersion]] = createPurposeVersion(purposeId, purposeVersion)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        createPurposeVersion200(PersistentPurposeVersion.toAPI(statusReply.getValue))
      case Success(statusReply) =>
        logger.error("Error while adding a version to purpose {}", purposeId, statusReply.getError)
        createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
      case Failure(ex) =>
        logger.error("Error while adding a version to purpose {}", purposeId, ex)
        createPurposeVersion400(problemOf(StatusCodes.BadRequest, CreatePurposeVersionBadRequest))
    }
  }

  override def activatePurposeVersion(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    // TODO This could deactivate old purpose
    logger.info("Activating purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      activatePurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => activatePurposeVersion204
      case statusReply if statusReply.isError =>
        logger.error("Error in activating purpose {} version {}", purposeId, versionId, statusReply.getError)
        activatePurposeVersion404(problemOf(StatusCodes.NotFound, ActivatePurposeNotFound))
    }
  }

  override def suspendPurposeVersion(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Suspending purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      suspendPurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => suspendPurposeVersion204
      case statusReply if statusReply.isError =>
        logger.error("Error in suspending purpose {} version {}", purposeId, versionId, statusReply.getError)
        suspendPurposeVersion404(problemOf(StatusCodes.NotFound, SuspendPurposeNotFound))
    }
  }

  override def archivePurposeVersion(
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Archiving purpose {} version {}", purposeId, versionId)
    val result: Future[StatusReply[PersistentPurpose]] =
      archivePurposeVersionById(purposeId, versionId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => archivePurposeVersion204
      case statusReply if statusReply.isError =>
        logger.error("Error in archiving purpose {} version {}", purposeId, versionId, statusReply.getError)
        archivePurposeVersion404(problemOf(StatusCodes.NotFound, ArchivePurposeNotFound))
    }
  }

  override def getPurposes(eserviceId: Option[String], consumerId: Option[String], states: String)(implicit
    toEntityMarshallerPurposearray: ToEntityMarshaller[Seq[Purpose]],
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
      purposes           = persistentPurposes.map(PersistentPurpose.toAPI)
    } yield purposes

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
    stateChangeDetails: StateChangeDetails
  ): Future[StatusReply[PersistentPurpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => ActivatePurposeVersion(purposeId, versionId, stateChangeDetails, ref))
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
      ListPurposes(from, to, consumerId, eserviceId, states.map(PersistentPurposeVersionState.fromApi), ref)

}
