package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersionState
}
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
      case CreatePurpose(newPurpose, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(newPurpose.id.toString)
        purpose
          .map { es =>
            replyTo ! StatusReply.Error[Purpose](s"Purpose ${es.id.toString} already exists")
            Effect.none[PurposeCreated, State]
          }
          .getOrElse {
            Effect
              .persist(PurposeCreated(newPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(PersistentPurpose.toAPI(newPurpose)))
          }

      case GetPurpose(purposeId, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        replyTo ! StatusReply.Success[Option[Purpose]](purpose.map(PersistentPurpose.toAPI))
        Effect.none[Event, State]

      case ActivatePurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        updatePurposeState(state, purposeId, versionId, PersistentPurposeVersionState.Active, stateChangeDetails)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurpose](s"Purpose $purposeId and version $versionId not found.")
            Effect.none[PurposeVersionActivated, State]
          } { updatedPurpose =>
            Effect
              .persist(PurposeVersionActivated(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(updatedPurpose))
          }

      case SuspendPurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        updatePurposeState(state, purposeId, versionId, PersistentPurposeVersionState.Suspended, stateChangeDetails)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurpose](s"Purpose $purposeId and version $versionId not found.")
            Effect.none[PurposeVersionSuspended, State]
          } { updatedPurpose =>
            Effect
              .persist(PurposeVersionSuspended(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(updatedPurpose))
          }

      case ArchivePurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        updatePurposeState(state, purposeId, versionId, PersistentPurposeVersionState.Archived, stateChangeDetails)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurpose](s"Purpose $purposeId and version $versionId not found.")
            Effect.none[PurposeVersionArchived, State]
          } { updatedPurpose =>
            Effect
              .persist(PurposeVersionArchived(updatedPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(updatedPurpose))
          }

      case ListPurposes(from, to, consumerId, eserviceId, purposeState, replyTo) =>
        val purposes: Seq[PersistentPurpose] = state.purposes
          .slice(from, to)
          .filter(purpose => consumerId.forall(_ == purpose._2.consumerId.toString))
          .filter(purpose => eserviceId.forall(_ == purpose._2.eserviceId.toString))
          .filter(purpose => purposeState.forall(state => purpose._2.versions.exists(_.state == state)))
          .values
          .toSeq

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
      case PurposeCreated(purpose)          => state.add(purpose)
      case PurposeVersionActivated(purpose) => state.updatePurpose(purpose)
      case PurposeVersionSuspended(purpose) => state.updatePurpose(purpose)
      case PurposeVersionArchived(purpose)  => state.updatePurpose(purpose)
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

  // TODO Test me
  private def updatePurposeState(
    state: State,
    purposeId: String,
    versionId: String,
    newVersionState: PersistentPurposeVersionState,
    stateChangeDetails: StateChangeDetails
  ): Option[PersistentPurpose] = {

    def isSuspended = newVersionState == PersistentPurposeVersionState.Suspended

    for {
      purpose <- state.purposes.get(purposeId)
      version <- purpose.versions.find(_.id.toString == versionId)
    } yield {

      val updatedVersions =
        purpose.versions.filterNot(_.id != version.id) :+ version

      stateChangeDetails.changedBy match {
        case Some(changedBy) =>
          changedBy match {
            case ChangedBy.CONSUMER =>
              purpose.copy(versions = updatedVersions, suspendedByConsumer = Some(isSuspended))
            case ChangedBy.PRODUCER =>
              purpose.copy(versions = updatedVersions, suspendedByProducer = Some(isSuspended))
          }
        case None => purpose.copy(versions = updatedVersions)
      }
    }

  }
}
