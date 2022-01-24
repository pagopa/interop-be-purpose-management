package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.purposemanagement.error.InternalErrors.{
  PurposeNotFound,
  PurposeVersionNotFound,
  PurposeVersionNotInDraft
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.purpose.{
  PersistentPurpose,
  PersistentPurposeVersion,
  PersistentPurposeVersionState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{ChangedBy, StateChangeDetails}
import it.pagopa.pdnd.interop.uservice.purposemanagement.service.OffsetDateTimeSupplier

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object PurposePersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command],
    dateTimeSupplier: OffsetDateTimeSupplier
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout =
      context.system.settings.config.getDuration("uservice-purpose-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case CreatePurpose(newPurpose, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(newPurpose.id.toString)
        purpose
          .fold {
            Effect
              .persist(PurposeCreated(newPurpose))
              .thenRun((_: State) => replyTo ! StatusReply.Success(newPurpose))
          } { p =>
            replyTo ! StatusReply.Error[PersistentPurpose](s"Purpose ${p.id.toString} already exists")
            Effect.none[PurposeCreated, State]
          }

      case CreatePurposeVersion(purposeId, newVersion, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)

        purpose
          .fold {
            replyTo ! StatusReply.Error[PersistentPurposeVersion](s"Purpose $purposeId not found")
            Effect.none[PurposeVersionCreated, State]
          } { _ =>
            Effect
              .persist(PurposeVersionCreated(purposeId, newVersion))
              .thenRun((_: State) => replyTo ! StatusReply.Success(newVersion))
          }

      case UpdatePurposeVersion(purposeId, versionId, update, replyTo) =>
        state
          .getPurposeVersion(purposeId, versionId)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurposeVersion](PurposeVersionNotFound(purposeId, versionId))
            Effect.none[PurposeVersionUpdated, State]
          } { v =>
            isDraftVersion(purposeId, v) match {
              case Right(_) =>
                val updatedVersion = v.update(update)
                Effect
                  .persist(PurposeVersionUpdated(purposeId, updatedVersion))
                  .thenRun((_: State) => replyTo ! StatusReply.Success(updatedVersion))
              case Left(ex) =>
                replyTo ! StatusReply.Error[PersistentPurposeVersion](ex)
                Effect.none[PurposeVersionUpdated, State]
            }
          }

      case GetPurpose(purposeId, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)
        replyTo ! StatusReply.Success[Option[PersistentPurpose]](purpose)
        Effect.none[Event, State]

      case ActivatePurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        def archiveOldVersion(purpose: PersistentPurpose, newActiveVersionId: String): PurposeVersionActivated = {
          val updatedVersions = purpose.versions.map { v =>
            v.state match {
              case PersistentPurposeVersionState.Active if v.id.toString != newActiveVersionId =>
                v.copy(state = PersistentPurposeVersionState.Archived)
              case _ => v
            }
          }
          PurposeVersionActivated(purpose.copy(versions = updatedVersions))
        }

        changeState(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          PersistentPurposeVersionState.Active,
          replyTo,
          _.isActivable(purposeId),
          archiveOldVersion(_, versionId)
        )(dateTimeSupplier)

      case SuspendPurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        changeState(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          PersistentPurposeVersionState.Suspended,
          replyTo,
          _.isSuspendable(purposeId),
          PurposeVersionSuspended
        )(dateTimeSupplier)

      case WaitForApprovalPurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        changeState(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          PersistentPurposeVersionState.WaitingForApproval,
          replyTo,
          _.canWaitForApproval(purposeId),
          PurposeVersionWaitedForApproval
        )(dateTimeSupplier)

      case ArchivePurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        changeState(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          PersistentPurposeVersionState.Archived,
          replyTo,
          _.isArchivable(purposeId),
          PurposeVersionArchived
        )(dateTimeSupplier)

      case ListPurposes(from, to, consumerId, eserviceId, purposeStates, replyTo) =>
        val purposes: Seq[PersistentPurpose] = state.purposes
          .slice(from, to)
          .filter(purpose => consumerId.forall(_ == purpose._2.consumerId.toString))
          .filter(purpose => eserviceId.forall(_ == purpose._2.eserviceId.toString))
          .filter(purpose =>
            purposeStates.isEmpty || purposeStates.intersect(purpose._2.versions.map(_.state)).nonEmpty
          )
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
      case PurposeCreated(purpose)                   => state.addPurpose(purpose)
      case PurposeVersionCreated(purposeId, version) => state.addPurposeVersion(purposeId, version)
      case PurposeVersionUpdated(purposeId, version) => state.addPurposeVersion(purposeId, version)
      case PurposeVersionActivated(purpose)          => state.updatePurpose(purpose)
      case PurposeVersionSuspended(purpose)          => state.updatePurpose(purpose)
      case PurposeVersionWaitedForApproval(purpose)  => state.updatePurpose(purpose)
      case PurposeVersionArchived(purpose)           => state.updatePurpose(purpose)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("uservice-purpose-management-persistence-purpose")

  def apply(
    shard: ActorRef[ClusterSharding.ShardCommand],
    persistenceId: PersistenceId,
    dateTimeSupplier: OffsetDateTimeSupplier
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(s"Starting Purpose Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config
          .getInt("uservice-purpose-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context, dateTimeSupplier),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(persistenceId.id))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }

  def updatePurposeFromState(
    purpose: PersistentPurpose,
    version: PersistentPurposeVersion,
    newVersionState: PersistentPurposeVersionState,
    stateChangeDetails: StateChangeDetails
  )(dateTimeSupplier: OffsetDateTimeSupplier): PersistentPurpose = {

    def isSuspended = newVersionState == PersistentPurposeVersionState.Suspended

    val timestamp = dateTimeSupplier.get

    def updateVersions(newState: PersistentPurposeVersionState): Seq[PersistentPurposeVersion] = {
      val updatedVersion = version.copy(state = newState, updatedAt = Some(timestamp))
      purpose.versions.filter(_.id != version.id) :+ updatedVersion
    }

    stateChangeDetails.changedBy match {
      case ChangedBy.CONSUMER =>
        val newState        = calcNewVersionState(purpose.suspendedByProducer, Some(isSuspended), newVersionState)
        val updatedVersions = updateVersions(newState)

        purpose.copy(versions = updatedVersions, suspendedByConsumer = Some(isSuspended), updatedAt = Some(timestamp))
      case ChangedBy.PRODUCER =>
        val newState        = calcNewVersionState(Some(isSuspended), purpose.suspendedByConsumer, newVersionState)
        val updatedVersions = updateVersions(newState)

        purpose.copy(versions = updatedVersions, suspendedByProducer = Some(isSuspended), updatedAt = Some(timestamp))
    }
  }

  def calcNewVersionState(
    suspendedByProducer: Option[Boolean],
    suspendedByConsumer: Option[Boolean],
    newState: PersistentPurposeVersionState
  ): PersistentPurposeVersionState = {
    import PersistentPurposeVersionState._
    (newState, suspendedByProducer, suspendedByConsumer) match {
      case (Active, Some(true), _) => Suspended
      case (Active, _, Some(true)) => Suspended
      case _                       => newState
    }
  }

  def isDraftVersion(purposeId: String, version: PersistentPurposeVersion): Either[Throwable, Unit] =
    version.state match {
      case PersistentPurposeVersionState.Draft =>
        Right(())
      case _ =>
        Left(PurposeVersionNotInDraft(purposeId, version.id.toString))
    }

  def changeState[T <: Event](
    state: State,
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails,
    newState: PersistentPurposeVersionState,
    replyTo: ActorRef[StatusReply[PersistentPurpose]],
    versionValidation: PersistentPurposeVersion => Either[Throwable, Unit],
    eventConstructor: PersistentPurpose => T
  )(dateTimeSupplier: OffsetDateTimeSupplier): EffectBuilder[T, State] = {
    val purpose: Either[Throwable, PersistentPurpose] = for {
      purpose <- state.purposes.get(purposeId).toRight(PurposeNotFound(purposeId))
      version <- purpose.versions.find(_.id.toString == versionId).toRight(PurposeVersionNotFound(purposeId, versionId))
      _       <- versionValidation(version)
    } yield updatePurposeFromState(purpose, version, newState, stateChangeDetails)(dateTimeSupplier)

    purpose
      .fold(
        ex => {
          replyTo ! StatusReply.Error[PersistentPurpose](ex)
          Effect.none[T, State]
        },
        updatedPurpose =>
          Effect
            .persist(eventConstructor(updatedPurpose))
            .thenRun((_: State) => replyTo ! StatusReply.Success(updatedPurpose))
      )
  }
}
