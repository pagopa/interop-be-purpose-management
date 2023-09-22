package it.pagopa.interop.purposemanagement.model.persistence.serializer

import cats.implicits._
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.munit.DiffxAssertions
import it.pagopa.interop.purposemanagement.model.persistence._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.PersistentSerializationSpec._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.purpose.PurposeStateV1._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.purpose._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.riskAnalysis._
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1.state._
import it.pagopa.interop.purposemanagement.model.purpose._
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import java.time.{OffsetDateTime, ZoneOffset}
import scala.reflect.runtime.universe.{TypeTag, typeOf}

class PersistentSerializationSpec extends ScalaCheckSuite with DiffxAssertions {

  serdeCheck[State, StateV1](stateGen, _.sorted)
  deserCheck[State, StateV1](stateGen)
  serdeCheck[PurposeCreated, PurposeCreatedV1](purposeCreatedGen)
  deserCheck[PurposeCreated, PurposeCreatedV1](purposeCreatedGen)
  serdeCheck[PurposeUpdated, PurposeUpdatedV1](purposeUpdatedGen)
  deserCheck[PurposeUpdated, PurposeUpdatedV1](purposeUpdatedGen)
  serdeCheck[PurposeVersionCreated, PurposeVersionCreatedV1](purposeVersionCreatedGen)
  deserCheck[PurposeVersionCreated, PurposeVersionCreatedV1](purposeVersionCreatedGen)
  serdeCheck[PurposeVersionActivated, PurposeVersionActivatedV1](purposeVersionActivatedGen)
  deserCheck[PurposeVersionActivated, PurposeVersionActivatedV1](purposeVersionActivatedGen)
  serdeCheck[PurposeVersionSuspended, PurposeVersionSuspendedV1](purposeVersionSuspendedGen)
  deserCheck[PurposeVersionSuspended, PurposeVersionSuspendedV1](purposeVersionSuspendedGen)
  serdeCheck[PurposeVersionArchived, PurposeVersionArchivedV1](purposeVersionArchivedGen)
  deserCheck[PurposeVersionArchived, PurposeVersionArchivedV1](purposeVersionArchivedGen)
  serdeCheck[PurposeVersionUpdated, PurposeVersionUpdatedV1](purposeVersionUpdatedGen)
  deserCheck[PurposeVersionUpdated, PurposeVersionUpdatedV1](purposeVersionUpdatedGen)
  serdeCheck[PurposeVersionDeleted, PurposeVersionDeletedV1](purposeVersionDeletedGen)
  deserCheck[PurposeVersionDeleted, PurposeVersionDeletedV1](purposeVersionDeletedGen)
  serdeCheck[PurposeDeleted, PurposeDeletedV1](purposeDeletedGen)
  deserCheck[PurposeDeleted, PurposeDeletedV1](purposeDeletedGen)
  serdeCheck[PurposeVersionWaitedForApproval, PurposeVersionWaitedForApprovalV1](purposeVersionWaitedForApprovalGen)
  deserCheck[PurposeVersionWaitedForApproval, PurposeVersionWaitedForApprovalV1](purposeVersionWaitedForApprovalGen)

  // TODO move me in commons
  def serdeCheck[A: TypeTag, B](gen: Gen[(A, B)], adapter: B => B = identity[B](_))(implicit
    e: PersistEventSerializer[A, B],
    loc: munit.Location,
    d: => Diff[Either[Throwable, B]]
  ): Unit = property(s"${typeOf[A].typeSymbol.name.toString} is correctly serialized") {
    forAll(gen) { case (state, stateV1) =>
      implicit val diffX: Diff[Either[Throwable, B]] = d
      assertEqual(PersistEventSerializer.to[A, B](state).map(adapter), Right(stateV1).map(adapter))
    }
  }

  // TODO move me in commons
  def deserCheck[A, B: TypeTag](
    gen: Gen[(A, B)]
  )(implicit e: PersistEventDeserializer[B, A], loc: munit.Location, d: => Diff[Either[Throwable, A]]): Unit =
    property(s"${typeOf[B].typeSymbol.name.toString} is correctly deserialized") {
      forAll(gen) { case (state, stateV1) =>
        // * This is declared lazy in the signature to avoid a MethodTooBigException
        implicit val diffX: Diff[Either[Throwable, A]] = d
        assertEqual(PersistEventDeserializer.from[B, A](stateV1), Right(state))
      }
    }
}

object PersistentSerializationSpec {

  val stringGen: Gen[String] = for {
    n <- Gen.chooseNum(4, 100)
    s <- Gen.containerOfN[List, Char](n, Gen.alphaNumChar)
  } yield s.foldLeft("")(_ + _)

  val offsetDatetimeGen: Gen[(OffsetDateTime, Long)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now      = OffsetDateTime.now(ZoneOffset.UTC)
    // Truncate to millis precision
    nowMills = now.withNano(now.getNano - (now.getNano % 1000000))
    time <- Gen.oneOf(nowMills.minusSeconds(n), nowMills.plusSeconds(n))
  } yield (time, time.toInstant.toEpochMilli)

  def listOf[T](g: => Gen[T]): Gen[List[T]] = for {
    n <- Gen.choose(0, 10)
    x <- Gen.listOfN(n, g)
  } yield x

  val persistentPurposeVersionState: Gen[(PersistentPurposeVersionState, PurposeStateV1)] = Gen.oneOf(
    (Draft, DRAFT),
    (Active, ACTIVE),
    (Suspended, SUSPENDED),
    (Archived, ARCHIVED),
    (WaitingForApproval, WAITING_FOR_APPROVAL)
  )

  val persistentPurposeVersionDocumentGen: Gen[(PersistentPurposeVersionDocument, PurposeVersionDocumentV1)] = for {
    id                       <- Gen.uuid
    contentType              <- stringGen
    path                     <- stringGen
    (createdAt, createdAtV1) <- offsetDatetimeGen
  } yield (
    PersistentPurposeVersionDocument(id, contentType, path, createdAt),
    PurposeVersionDocumentV1(id.toString(), contentType, path, createdAtV1)
  )

  val persistentPurposeVersionGen: Gen[(PersistentPurposeVersion, PurposeVersionV1)] = for {
    id                                             <- Gen.uuid
    (state, stateV1)                               <- persistentPurposeVersionState
    (expectedApprovalDate, expectedApprovalDateV1) <- Gen.option(offsetDatetimeGen).map(_.separate)
    (riskAnalysis, riskAnalysisV1)                 <- Gen.option(persistentPurposeVersionDocumentGen).map(_.separate)
    dailyCalls                                     <- Gen.posNum[Int]
    (createdAt, createdAtV1)                       <- offsetDatetimeGen
    (updatedAt, updatedAtV1)                       <- Gen.option(offsetDatetimeGen).map(_.separate)
    (firstActivationAt, firstActivationAtV1)       <- Gen.option(offsetDatetimeGen).map(_.separate)
    (suspendedAt, suspendedAtV1)                   <- Gen.option(offsetDatetimeGen).map(_.separate)

  } yield (
    PersistentPurposeVersion(
      id = id,
      state = state,
      expectedApprovalDate = expectedApprovalDate,
      riskAnalysis = riskAnalysis,
      dailyCalls = dailyCalls,
      createdAt = createdAt,
      updatedAt = updatedAt,
      firstActivationAt = firstActivationAt,
      suspendedAt = suspendedAt
    ),
    PurposeVersionV1(
      id = id.toString(),
      state = stateV1,
      riskAnalysis = riskAnalysisV1,
      dailyCalls = dailyCalls,
      createdAt = createdAtV1,
      updatedAt = updatedAtV1,
      firstActivationAt = firstActivationAtV1,
      expectedApprovalDate = expectedApprovalDateV1,
      suspendedAt = suspendedAtV1
    )
  )

  val persistentRiskAnalysisSingleAnswerGen: Gen[(PersistentRiskAnalysisSingleAnswer, RiskAnalysisSingleAnswerV1)] =
    for {
      id    <- Gen.uuid
      key   <- stringGen
      value <- Gen.option(stringGen)
    } yield (PersistentRiskAnalysisSingleAnswer(id, key, value), RiskAnalysisSingleAnswerV1(id.toString(), key, value))

  val persistentRiskAnalysisMultiAnswerGen: Gen[(PersistentRiskAnalysisMultiAnswer, RiskAnalysisMultiAnswerV1)] =
    for {
      id    <- Gen.uuid
      key   <- stringGen
      value <- listOf(stringGen)
    } yield (PersistentRiskAnalysisMultiAnswer(id, key, value), RiskAnalysisMultiAnswerV1(id.toString(), key, value))

  val persistentRiskAnalysisFormGen: Gen[(PersistentRiskAnalysisForm, RiskAnalysisFormV1)] = for {
    id                               <- Gen.uuid
    riskAnalysisId                   <- Gen.uuid
    version                          <- stringGen
    (singleAnswers, singleAnswersV1) <- listOf(persistentRiskAnalysisSingleAnswerGen).map(_.separate)
    (multiAnswers, multiAnswersV1)   <- listOf(persistentRiskAnalysisMultiAnswerGen).map(_.separate)
  } yield (
    PersistentRiskAnalysisForm(id, riskAnalysisId.some, version, singleAnswers, multiAnswers),
    RiskAnalysisFormV1(id.toString(), version, singleAnswersV1, multiAnswersV1, riskAnalysisId.toString().some)
  )

  val persistentPurposeGen: Gen[(PersistentPurpose, PurposeV1)] = for {
    id                                     <- Gen.uuid
    eserviceId                             <- Gen.uuid
    consumerId                             <- Gen.uuid
    (versions, versionsV1)                 <- listOf(persistentPurposeVersionGen).map(_.separate)
    suspendedByConsumer                    <- Gen.option(Gen.oneOf(true, false))
    suspendedByProducer                    <- Gen.option(Gen.oneOf(true, false))
    title                                  <- stringGen
    description                            <- stringGen
    (riskAnalysisForm, riskAnalysisFormV1) <- Gen.option(persistentRiskAnalysisFormGen).map(_.separate)
    (createdAt, createdAtV1)               <- offsetDatetimeGen
    (updatedAt, updatedAtV1)               <- Gen.option(offsetDatetimeGen).map(_.separate)
    isFreeOfCharge                         <- Gen.oneOf(true, false)
    freeOfChargeReason                     <- if (isFreeOfCharge) Gen.some(stringGen) else Gen.fail
  } yield (
    PersistentPurpose(
      id = id,
      eserviceId = eserviceId,
      consumerId = consumerId,
      versions = versions,
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      title = title,
      description = description,
      riskAnalysisForm = riskAnalysisForm,
      createdAt = createdAt,
      updatedAt = updatedAt,
      isFreeOfCharge = isFreeOfCharge,
      freeOfChargeReason = freeOfChargeReason
    ),
    PurposeV1(
      id = id.toString(),
      eserviceId = eserviceId.toString(),
      consumerId = consumerId.toString(),
      suspendedByConsumer = suspendedByConsumer,
      suspendedByProducer = suspendedByProducer,
      versions = versionsV1,
      title = title,
      description = description,
      riskAnalysisForm = riskAnalysisFormV1,
      createdAt = createdAtV1,
      updatedAt = updatedAtV1,
      isFreeOfCharge = Some(isFreeOfCharge),
      freeOfChargeReason = freeOfChargeReason
    )
  )

  val stateGen: Gen[(State, StateV1)] = listOf(persistentPurposeGen).map(_.separate).map { case (ps, psv1) =>
    val state   = State(ps.map(p => p.id.toString -> p).toMap)
    val stateV1 = StateV1(psv1.map(pV1 => PurposesV1(pV1.id, pV1)))
    (state, stateV1)
  }

  implicit class PimpedStateV1(val stateV1: StateV1) extends AnyVal {
    def sorted: StateV1 = stateV1.copy(stateV1.purposes.sortBy(_.key))
  }

  val purposeCreatedGen: Gen[(PurposeCreated, PurposeCreatedV1)] = persistentPurposeGen.map { case (a, b) =>
    (PurposeCreated(a), PurposeCreatedV1(b))
  }

  val purposeUpdatedGen: Gen[(PurposeUpdated, PurposeUpdatedV1)] = persistentPurposeGen.map { case (a, b) =>
    (PurposeUpdated(a), PurposeUpdatedV1(b))
  }

  val purposeVersionActivatedGen: Gen[(PurposeVersionActivated, PurposeVersionActivatedV1)] = persistentPurposeGen.map {
    case (a, b) => (PurposeVersionActivated(a), PurposeVersionActivatedV1(b))
  }

  val purposeVersionSuspendedGen: Gen[(PurposeVersionSuspended, PurposeVersionSuspendedV1)] = persistentPurposeGen.map {
    case (a, b) => (PurposeVersionSuspended(a), PurposeVersionSuspendedV1(b))
  }

  val purposeVersionWaitedForApprovalGen: Gen[(PurposeVersionWaitedForApproval, PurposeVersionWaitedForApprovalV1)] =
    persistentPurposeGen.map { case (a, b) =>
      (PurposeVersionWaitedForApproval(a), PurposeVersionWaitedForApprovalV1(b))
    }

  val purposeVersionArchivedGen: Gen[(PurposeVersionArchived, PurposeVersionArchivedV1)] = persistentPurposeGen.map {
    case (a, b) => (PurposeVersionArchived(a), PurposeVersionArchivedV1(b))
  }

  val purposeVersionCreatedGen: Gen[(PurposeVersionCreated, PurposeVersionCreatedV1)] = for {
    purposeId                          <- stringGen
    (purposeVersion, purposeVersionV1) <- persistentPurposeVersionGen
  } yield (PurposeVersionCreated(purposeId, purposeVersion), PurposeVersionCreatedV1(purposeId, purposeVersionV1))

  val purposeVersionUpdatedGen: Gen[(PurposeVersionUpdated, PurposeVersionUpdatedV1)] = for {
    purposeId                          <- stringGen
    (purposeVersion, purposeVersionV1) <- persistentPurposeVersionGen
  } yield (PurposeVersionUpdated(purposeId, purposeVersion), PurposeVersionUpdatedV1(purposeId, purposeVersionV1))

  val purposeVersionDeletedGen: Gen[(PurposeVersionDeleted, PurposeVersionDeletedV1)] = for {
    purposeId <- stringGen
    versionId <- stringGen
  } yield (PurposeVersionDeleted(purposeId, versionId), PurposeVersionDeletedV1(purposeId, versionId))

  val purposeDeletedGen: Gen[(PurposeDeleted, PurposeDeletedV1)] =
    stringGen.map(s => (PurposeDeleted(s), PurposeDeletedV1(s)))

}
