package it.pagopa.interop.purposemanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EffectBuilder, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.interop.purposemanagement.error.PurposeManagementErrors._
import it.pagopa.interop.purposemanagement.model.persistence.Adapters._
import it.pagopa.interop.purposemanagement.model.purpose._
import it.pagopa.interop.purposemanagement.model.{ChangedBy, PurposeVersionDocument, StateChangeDetails}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object PurposePersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout =
      context.system.settings.config.getDuration("purpose-management.idle-timeout")
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
            replyTo ! StatusReply.Error[PersistentPurpose](PurposeCreationConflict(p.id))
            Effect.none[PurposeCreated, State]
          }

      case DeletePurpose(purposeId, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)

        purpose
          .fold {
            replyTo ! StatusReply.Error[Unit](PurposeNotFound(purposeId))
            Effect.none[PurposeDeleted, State]
          } { p =>
            if (p.versions.isEmpty) {
              Effect
                .persist(PurposeDeleted(purposeId))
                .thenRun((_: State) => replyTo ! StatusReply.Success(()))
            } else {
              replyTo ! StatusReply.Error[Unit](PurposeVersionsNotEmpty(purposeId))
              Effect.none[PurposeDeleted, State]
            }
          }

      case CreatePurposeVersion(purposeId, newVersion, replyTo) =>
        val purpose: Option[PersistentPurpose] = state.purposes.get(purposeId)

        purpose
          .fold {
            replyTo ! StatusReply.Error[PersistentPurposeVersion](PurposeNotFound(purposeId))
            Effect.none[PurposeVersionCreated, State]
          } { p =>
            val conflictVersion = p.versions.find(v => v.state == Draft || v.state == WaitingForApproval)
            conflictVersion match {
              case Some(version) =>
                replyTo ! StatusReply.Error[PersistentPurposeVersion](
                  PurposeVersionStateConflict(purposeId, version.id.toString, version.state)
                )
                Effect.none[PurposeVersionCreated, State]
              case None          =>
                Effect
                  .persist(PurposeVersionCreated(purposeId, newVersion))
                  .thenRun((_: State) => replyTo ! StatusReply.Success(newVersion))
            }
          }

      case DeletePurposeVersion(purposeId, versionId, replyTo) =>
        val version: Option[PersistentPurposeVersion] = state.getPurposeVersion(purposeId, versionId)

        version
          .fold {
            replyTo ! StatusReply.Error[Unit](PurposeVersionNotFound(purposeId, versionId))
            Effect.none[PurposeVersionDeleted, State]
          } { v =>
            v.state match {
              case Draft | WaitingForApproval =>
                Effect
                  .persist(PurposeVersionDeleted(purposeId, versionId))
                  .thenRun((_: State) => replyTo ! StatusReply.Success(()))
              case versionState               =>
                replyTo ! StatusReply.Error[Unit](NotAllowedForPurposeVersionState(purposeId, versionId, versionState))
                Effect.none[PurposeVersionDeleted, State]
            }
          }

      case UpdatePurpose(purposeId, update, replyTo) =>
        state.purposes
          .get(purposeId)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurpose](PurposeNotFound(purposeId))
            Effect.none[PurposeUpdated, State]
          } { purpose =>
            val notDraftVersion = purpose.versions.map(isDraftVersion(purposeId, _)).find(_.isLeft)
            notDraftVersion match {
              case Some(_ @Left(ex)) =>
                replyTo ! StatusReply.Error[PersistentPurpose](ex)
                Effect.none[PurposeUpdated, State]
              case _                 =>
                val updatedPurpose = purpose.update(update)
                Effect
                  .persist(PurposeUpdated(updatedPurpose))
                  .thenRun((_: State) => replyTo ! StatusReply.Success(updatedPurpose))

            }
          }

      case UpdateDraftPurposeVersion(purposeId, versionId, update, replyTo) =>
        state
          .getPurposeVersion(purposeId, versionId)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurposeVersion](PurposeVersionNotFound(purposeId, versionId))
            Effect.none[PurposeVersionUpdated, State]
          } { v =>
            isDraftVersion(purposeId, v) match {
              case Right(_) =>
                val updatedVersion = v.copy(dailyCalls = update.dailyCalls, updatedAt = Some(update.timestamp))
                Effect
                  .persist(PurposeVersionUpdated(purposeId, updatedVersion))
                  .thenRun((_: State) => replyTo ! StatusReply.Success(updatedVersion))
              case Left(ex) =>
                replyTo ! StatusReply.Error[PersistentPurposeVersion](ex)
                Effect.none[PurposeVersionUpdated, State]
            }
          }

      case UpdateWaitingForApprovalPurposeVersion(purposeId, versionId, update, replyTo) =>
        state
          .getPurposeVersion(purposeId, versionId)
          .fold {
            replyTo ! StatusReply.Error[PersistentPurposeVersion](PurposeVersionNotFound(purposeId, versionId))
            Effect.none[PurposeVersionUpdated, State]
          } { v =>
            isWaitingForApprovalVersion(purposeId, v) match {
              case Right(_) =>
                val updatedVersion =
                  v.copy(expectedApprovalDate = Some(update.expectedApprovalDate), updatedAt = Some(update.timestamp))
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

      case ActivatePurposeVersion(purposeId, versionId, versionDocument, stateChangeDetails, replyTo) =>
        val purpose: Either[Throwable, PersistentPurpose] = getModifiedPurpose(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          Active,
          _.isActivable(purposeId),
          versionDocument
        )

        def archiveOldVersion(purpose: PersistentPurpose, newActiveVersionId: String): PersistentPurpose =
          purpose.copy(versions = purpose.versions.map { v =>
            v.state match {
              case Active | Suspended if v.id.toString != newActiveVersionId => v.copy(state = Archived)
              case _                                                         => v
            }
          })

        purpose
          .fold(
            handleFailure[PurposeVersionActivated](_)(replyTo),
            purpose => {
              val updatedPurpose: PersistentPurpose = archiveOldVersion(purpose, versionId)
              persistStateAndReply(updatedPurpose, PurposeVersionActivated)(replyTo)
            }
          )

      case SuspendPurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        val purpose: Either[Throwable, PersistentPurpose] =
          getModifiedPurpose(state, purposeId, versionId, stateChangeDetails, Suspended, _.isSuspendable(purposeId))

        purpose
          .fold(
            handleFailure[PurposeVersionSuspended](_)(replyTo),
            persistStateAndReply(_, PurposeVersionSuspended)(replyTo)
          )

      case WaitForApprovalPurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        val purpose: Either[Throwable, PersistentPurpose] = getModifiedPurpose(
          state,
          purposeId,
          versionId,
          stateChangeDetails,
          WaitingForApproval,
          _.canWaitForApproval(purposeId)
        )

        purpose
          .fold(
            handleFailure[PurposeVersionWaitedForApproval](_)(replyTo),
            persistStateAndReply(_, PurposeVersionWaitedForApproval)(replyTo)
          )

      case ArchivePurposeVersion(purposeId, versionId, stateChangeDetails, replyTo) =>
        val purpose: Either[Throwable, PersistentPurpose] =
          getModifiedPurpose(state, purposeId, versionId, stateChangeDetails, Archived, _.isArchivable(purposeId))

        purpose
          .fold(
            handleFailure[PurposeVersionArchived](_)(replyTo),
            persistStateAndReply(_, PurposeVersionArchived)(replyTo)
          )

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
        context.log.debug(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  def handleFailure[T](ex: Throwable)(replyTo: ActorRef[StatusReply[PersistentPurpose]]): EffectBuilder[T, State] = {
    replyTo ! StatusReply.Error[PersistentPurpose](ex)
    Effect.none[T, State]
  }

  def persistStateAndReply[T](purpose: PersistentPurpose, eventBuilder: PersistentPurpose => T)(
    replyTo: ActorRef[StatusReply[PersistentPurpose]]
  ): EffectBuilder[T, State] = Effect
    .persist(eventBuilder(purpose))
    .thenRun((_: State) => replyTo ! StatusReply.Success(purpose))

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case PurposeCreated(purpose)                     => state.addPurpose(purpose)
      case PurposeUpdated(purpose)                     => state.updatePurpose(purpose)
      case PurposeDeleted(purposeId)                   => state.removePurpose(purposeId)
      case PurposeVersionCreated(purposeId, version)   => state.addPurposeVersion(purposeId, version)
      case PurposeVersionUpdated(purposeId, version)   => state.addPurposeVersion(purposeId, version)
      case PurposeVersionDeleted(purposeId, versionId) => state.removePurposeVersion(purposeId, versionId)
      case PurposeVersionActivated(purpose)            => state.updatePurpose(purpose)
      case PurposeVersionSuspended(purpose)            => state.updatePurpose(purpose)
      case PurposeVersionWaitedForApproval(purpose)    => state.updatePurpose(purpose)
      case PurposeVersionArchived(purpose)             => state.updatePurpose(purpose)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("interop-be-purpose-management-persistence")

  def apply(
    shard: ActorRef[ClusterSharding.ShardCommand],
    persistenceId: PersistenceId,
    persistenceTag: String
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.debug(s"Starting Purpose Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config
          .getInt("purpose-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(persistenceTag))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }

  def updatePurposeFromState(
    purpose: PersistentPurpose,
    version: PersistentPurposeVersion,
    newVersionState: PersistentPurposeVersionState,
    stateChangeDetails: StateChangeDetails
  ): PersistentPurpose = {

    def isSuspended = newVersionState == Suspended

    def updateVersions(newState: PersistentPurposeVersionState): Seq[PersistentPurposeVersion] = {
      val firstActivationAt: Option[OffsetDateTime] = version.state match {
        case Draft | WaitingForApproval => Some(stateChangeDetails.timestamp)
        case _                          => version.firstActivationAt
      }

      val suspendedAt: Option[OffsetDateTime] = (version.state, newState) match {
        case (Suspended, Suspended) => version.suspendedAt
        case (_, Suspended)         => Some(stateChangeDetails.timestamp)
        case _                      => None
      }

      val updatedVersion =
        version.copy(
          state = newState,
          updatedAt = Some(stateChangeDetails.timestamp),
          firstActivationAt = firstActivationAt,
          suspendedAt = suspendedAt
        )
      purpose.versions.filter(_.id != version.id) :+ updatedVersion
    }

    stateChangeDetails.changedBy match {
      case ChangedBy.CONSUMER =>
        val suspendedByRequester = isSuspended ||
          (newVersionState == WaitingForApproval && stateChangeDetails.changedBy == ChangedBy.CONSUMER && purpose.suspendedByConsumer
            .contains(true))
        val newState = calcNewVersionState(purpose.suspendedByProducer, Some(suspendedByRequester), newVersionState)
        val updatedVersions = updateVersions(newState)

        purpose.copy(
          versions = updatedVersions,
          suspendedByConsumer = Some(suspendedByRequester),
          updatedAt = Some(stateChangeDetails.timestamp)
        )
      case ChangedBy.PRODUCER =>
        if (version.state == WaitingForApproval && newVersionState == Active) {
          // Force the state to Active when enabling a version in WaitingForApproval
          val updatedVersions = updateVersions(newVersionState)

          purpose.copy(
            versions = updatedVersions,
            suspendedByConsumer = Some(false),
            suspendedByProducer = Some(false),
            updatedAt = Some(stateChangeDetails.timestamp)
          )
        } else {
          val suspendedByRequester = isSuspended ||
            (newVersionState == WaitingForApproval && stateChangeDetails.changedBy == ChangedBy.PRODUCER && purpose.suspendedByProducer
              .contains(true))
          val newState = calcNewVersionState(Some(suspendedByRequester), purpose.suspendedByConsumer, newVersionState)
          val updatedVersions = updateVersions(newState)

          purpose.copy(
            versions = updatedVersions,
            suspendedByProducer = Some(suspendedByRequester),
            updatedAt = Some(stateChangeDetails.timestamp)
          )
        }

    }
  }

  def calcNewVersionState(
    suspendedByProducer: Option[Boolean],
    suspendedByConsumer: Option[Boolean],
    newState: PersistentPurposeVersionState
  ): PersistentPurposeVersionState = (newState, suspendedByProducer, suspendedByConsumer) match {
    case (Active, Some(true), _) => Suspended
    case (Active, _, Some(true)) => Suspended
    case _                       => newState
  }

  def isDraftVersion(purposeId: String, version: PersistentPurposeVersion): Either[Throwable, Unit] =
    version.state match {
      case Draft => Right(())
      case v     => Left(NotAllowedForPurposeVersionState(purposeId, version.id.toString, v))
    }

  def isWaitingForApprovalVersion(purposeId: String, version: PersistentPurposeVersion): Either[Throwable, Unit] =
    version.state match {
      case WaitingForApproval => Right(())
      case v                  => Left(NotAllowedForPurposeVersionState(purposeId, version.id.toString, v))
    }

  def getModifiedPurpose[T <: Event](
    state: State,
    purposeId: String,
    versionId: String,
    stateChangeDetails: StateChangeDetails,
    newState: PersistentPurposeVersionState,
    versionValidation: PersistentPurposeVersion => Either[Throwable, Unit],
    riskAnalysisOpt: Option[PurposeVersionDocument] = None
  ): Either[Throwable, PersistentPurpose] = for {
    purpose <- state.purposes.get(purposeId).toRight(PurposeNotFound(purposeId))
    version <- purpose.versions.find(_.id.toString == versionId).toRight(PurposeVersionNotFound(purposeId, versionId))
    riskAnalysisUpdated = riskAnalysisOpt.map(PersistentPurposeVersionDocument.fromAPI).orElse(version.riskAnalysis)
    versionWithRisk     = version.copy(riskAnalysis = riskAnalysisUpdated)
    _ <- versionValidation(versionWithRisk)
  } yield updatePurposeFromState(purpose, versionWithRisk, newState, stateChangeDetails)

}
