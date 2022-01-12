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
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeState}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence._
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

  /** Code: 200, Message: Purpose created, DataType: Purpose
    * Code: 405, Message: Invalid input, DataType: Problem
    */
  override def addPurpose(purposeSeed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info(
      "Adding a purpose for consumer {} to e-service {} with state {}",
      purposeSeed.consumerId,
      purposeSeed.eserviceId,
      purposeSeed.state
    )
    val purpose: PersistentPurpose           = PersistentPurpose.fromAPI(purposeSeed, UUIDSupplier, dateTimeSupplier)
    val result: Future[StatusReply[Purpose]] = createPurpose(purpose)
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess => addPurpose200(statusReply.getValue)
      case Success(statusReply) =>
        logger.error(
          "Error while adding a purpose for consumer {} to e-service {} with state {}",
          purposeSeed.consumerId,
          purposeSeed.eserviceId,
          purposeSeed.state,
          statusReply.getError
        )
        addPurpose409(problemOf(StatusCodes.Conflict, AddPurposeConflict))
      case Failure(ex) =>
        logger.error(
          "Error while adding a purpose for consumer {} to e-service {} with state {}",
          purposeSeed.consumerId,
          purposeSeed.eserviceId,
          purposeSeed.state,
          ex
        )
        addPurpose400(problemOf(StatusCodes.BadRequest, AddPurposeBadRequest))
    }
  }

  /** Code: 200, Message: EService retrieved, DataType: Purpose
    * Code: 404, Message: Purpose not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def getPurpose(purposeId: String)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting purpose {}", purposeId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))
    val result: Future[StatusReply[Option[Purpose]]] = commander.ask(ref => GetPurpose(purposeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.fold(getPurpose404(problemOf(StatusCodes.NotFound, GetPurposeNotFound)))(purpose =>
          getPurpose200(purpose)
        )
      case statusReply if statusReply.isError =>
        logger.error("Error in getting purpose {}", purposeId, statusReply.getError)
        getPurpose400(problemOf(StatusCodes.BadRequest, GetPurposeBadRequest))
    }
  }

  override def activatePurpose(purposeId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    // TODO This could deactivate old purpose
    logger.info("Activating purpose {}", purposeId)
    val result: Future[StatusReply[Purpose]] = activatePurposeById(purposeId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => activatePurpose200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        logger.error("Error in activating purpose {}", purposeId, statusReply.getError)
        activatePurpose404(problemOf(StatusCodes.NotFound, ActivatePurposeNotFound))
    }
  }

  override def suspendPurpose(purposeId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Suspending purpose {}", purposeId)
    val result: Future[StatusReply[Purpose]] = suspendPurposeById(purposeId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => suspendPurpose200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        logger.error("Error in suspending purpose {}", purposeId, statusReply.getError)
        suspendPurpose404(problemOf(StatusCodes.NotFound, SuspendPurposeNotFound))
    }
  }

  override def archivePurpose(purposeId: String, stateChangeDetails: StateChangeDetails)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Archiving purpose {}", purposeId)
    val result: Future[StatusReply[Purpose]] = archivePurposeById(purposeId, stateChangeDetails)
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => archivePurpose200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        logger.error("Error in archiving purpose {}", purposeId, statusReply.getError)
        archivePurpose404(problemOf(StatusCodes.NotFound, ArchivePurposeNotFound))
    }
  }

  /** Code: 200, Message: A list of Purpose, DataType: Seq[Purpose]
    */
  override def getPurposes(consumerId: Option[String], eserviceId: Option[String], states: String)(implicit
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
      statesEnum <- parseArrayParameters(states).traverse(PurposeState.fromValue)
      generator = createListPurposesGenerator(consumerId = consumerId, eserviceId = eserviceId, states = statesEnum)(
        _,
        _
      )
      purposes = commanders.flatMap(ref => slices(ref, sliceSize)(generator))
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

  private def createPurpose(purpose: PersistentPurpose): Future[StatusReply[Purpose]] = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(
        PurposePersistentBehavior.TypeKey,
        AkkaUtils.getShard(purpose.id.toString, settings.numberOfShards)
      )

    commander.ask(ref => AddPurpose(purpose, ref))
  }

  private def activatePurposeById(purposeId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => ActivatePurpose(purposeId, stateChangeDetails, ref))
  }

  private def suspendPurposeById(purposeId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => SuspendPurpose(purposeId, stateChangeDetails, ref))
  }

  private def archivePurposeById(purposeId: String, stateChangeDetails: StateChangeDetails) = {
    val commander: EntityRef[Command] =
      sharding.entityRefFor(PurposePersistentBehavior.TypeKey, AkkaUtils.getShard(purposeId, settings.numberOfShards))

    commander.ask(ref => ArchivePurpose(purposeId, stateChangeDetails, ref))
  }

  private def createListPurposesGenerator(
    consumerId: Option[String],
    eserviceId: Option[String],
    states: List[PurposeState]
  )(from: Int, to: Int): ActorRef[Seq[Purpose]] => ListPurposes =
    (ref: ActorRef[Seq[Purpose]]) =>
      ListPurposes(from, to, consumerId, eserviceId, states.map(PersistentPurposeState.fromApi), ref)

}
