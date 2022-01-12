package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{PersistentPurpose, PersistentPurposeState}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{ChangedBy, Purpose, StateChangeDetails}

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object PurposePersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout =
      context.system.settings.config.getDuration("uservice-purpose-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddPurpose(newPurpose, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(newPurpose.id.toString)
        purpose
          .map { es =>
            replyTo ! StatusReply.Error[Purpose](s"Purpose ${es.id.toString} already exists")
            Effect.none[PurposeAdded, State]
          }
          .getOrElse {
            Effect
              .persist(PurposeAdded(newPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(newPurpose)))
          }

      case GetPurpose(purposeId, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        replyTo ! StatusReply.Success[Option[Purpose]](purpose.map(PersistentPurpose.toAPI))
        Effect.none[Event, State]

      case ActivatePurpose(purposeId, stateChangeDetails, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        purpose
          .map { purpose =>
            val updatedPurpose =
              updatePurposeState(purpose, PersistentPurposeState.Active, stateChangeDetails)
            Effect
              .persist(PurposeActivated(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(updatedPurpose)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Purpose](s"Purpose $purposeId not found.")
            Effect.none[PurposeActivated, State]
          }

      case SuspendPurpose(purposeId, stateChangeDetails, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        purpose
          .map { purpose =>
            val updatedPurpose =
              updatePurposeState(purpose, PersistentPurposeState.Suspended, stateChangeDetails)
            Effect
              .persist(PurposeSuspended(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(updatedPurpose)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Purpose](s"Purpose $purposeId not found.")
            Effect.none[PurposeSuspended, State]
          }

      case ArchivePurpose(purposeId, stateChangeDetails, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        purpose
          .map { purpose =>
            val updatedPurpose =
              updatePurposeState(purpose, PersistentPurposeState.Archived, stateChangeDetails)
            Effect
              .persist(PurposeArchived(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(updatedPurpose)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Purpose](s"Purpose $purposeId not found.")
            Effect.none[PurposeArchived, State]
          }

      case WaitForPurposeApproval(purposeId, stateChangeDetails, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        purpose
          .map { purpose =>
            val updatedPurpose =
              updatePurposeState(purpose, PersistentPurposeState.WaitingForApproval, stateChangeDetails)
            Effect
              .persist(PurposeArchived(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(updatedPurpose)))
          }
          .getOrElse {
            replyTo ! StatusReply.Error[Purpose](s"Purpose $purposeId not found.")
            Effect.none[PurposeArchived, State]
          }

      case ListPurposes(from, to, consumerId, eserviceId, purposeState, replyTo) =>
        val purposes: Seq[Purpose] = state.purposes
          .slice(from, to)
          .filter(purpose => consumerId.forall(filter => filter == purpose._2.consumerId.toString))
          .filter(purpose => eserviceId.forall(filter => filter == purpose._2.eserviceId.toString))
          .filter(purpose => purposeState.forall(filter => filter == purpose._2.state))
          .values
          .toSeq
          .map(PersistentPurpose.toAPI)

        replyTo ! purposes
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.info(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case PurposeAdded(purpose)              => state.add(purpose)
      case PurposeActivated(purpose)          => state.updatePurpose(purpose)
      case PurposeSuspended(purpose)          => state.updatePurpose(purpose)
      case PurposeArchived(purpose)           => state.updatePurpose(purpose)
      case PurposeWaitingForApproval(purpose) => state.updatePurpose(purpose)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("uservice-purpose-management-persistence-purpose")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(s"Starting Purpose Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config
          .getInt("uservice-purpose-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(persistenceId.id))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }

  private def updatePurposeState(
    persistentPurpose: PersistentPurpose,
    state: PersistentPurposeState,
    stateChangeDetails: StateChangeDetails
  ): PersistentPurpose = {

    def isSuspended = state == PersistentPurposeState.Suspended

    stateChangeDetails.changedBy match {
      case Some(changedBy) =>
        changedBy match {
          case ChangedBy.CONSUMER => persistentPurpose.copy(state = state, suspendedByConsumer = Some(isSuspended))
          case ChangedBy.PRODUCER => persistentPurpose.copy(state = state, suspendedByProducer = Some(isSuspended))
        }
      case None => persistentPurpose.copy(state = state)
    }

  }
}
