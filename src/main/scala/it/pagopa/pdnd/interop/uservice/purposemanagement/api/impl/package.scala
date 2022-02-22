package it.pagopa.pdnd.interop.uservice.purposemanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import akka.util.Timeout
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import it.pagopa.pdnd.interop.uservice.purposemanagement.model._
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.Command
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val riskAnalysisSingleAnswerFormat: RootJsonFormat[RiskAnalysisSingleAnswer] =
    jsonFormat3(RiskAnalysisSingleAnswer)
  implicit val riskAnalysisMultiAnswerFormat: RootJsonFormat[RiskAnalysisMultiAnswer] =
    jsonFormat3(RiskAnalysisMultiAnswer)
  implicit val riskAnalysisFormFormat: RootJsonFormat[RiskAnalysisForm] = jsonFormat4(RiskAnalysisForm)
  implicit val riskAnalysisSingleAnswerSeedFormat: RootJsonFormat[RiskAnalysisSingleAnswerSeed] =
    jsonFormat2(RiskAnalysisSingleAnswerSeed)
  implicit val riskAnalysisMultiAnswerSeedFormat: RootJsonFormat[RiskAnalysisMultiAnswerSeed] =
    jsonFormat2(RiskAnalysisMultiAnswerSeed)
  implicit val riskAnalysisFormSeedFormat: RootJsonFormat[RiskAnalysisFormSeed] = jsonFormat3(RiskAnalysisFormSeed)
  implicit val purposeSeedFormat: RootJsonFormat[PurposeSeed]                   = jsonFormat5(PurposeSeed)
  implicit val PurposeUpdateContentFormat: RootJsonFormat[PurposeUpdateContent] = jsonFormat3(PurposeUpdateContent)
  implicit val versionDocumentFormat: RootJsonFormat[PurposeVersionDocument]    = jsonFormat4(PurposeVersionDocument)
  implicit val purposeVersionSeedFormat: RootJsonFormat[PurposeVersionSeed]     = jsonFormat2(PurposeVersionSeed)
  implicit val draftPurposeVersionUpdateFormat: RootJsonFormat[DraftPurposeVersionUpdateContent] =
    jsonFormat1(DraftPurposeVersionUpdateContent)
  implicit val waitingForApprovalPurposeVersionUpdateFormat
    : RootJsonFormat[WaitingForApprovalPurposeVersionUpdateContent] =
    jsonFormat1(WaitingForApprovalPurposeVersionUpdateContent)
  implicit val purposeVersionFormat: RootJsonFormat[PurposeVersion]         = jsonFormat8(PurposeVersion)
  implicit val purposeFormat: RootJsonFormat[Purpose]                       = jsonFormat11(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes]                     = jsonFormat1(Purposes)
  implicit val stateChangeDetailsFormat: RootJsonFormat[StateChangeDetails] = jsonFormat1(StateChangeDetails)
  implicit val problemErrorFormat: RootJsonFormat[ProblemError]             = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                       = jsonFormat5(Problem)
  implicit val activatePurposeVersionPayloadFormat: RootJsonFormat[ActivatePurposeVersionPayload] =
    jsonFormat2(ActivatePurposeVersionPayload)

  def slices[A, B <: Command](commander: EntityRef[B], sliceSize: Int)(
    commandGenerator: (Int, Int) => ActorRef[Seq[A]] => B
  )(implicit timeout: Timeout): LazyList[A] = {
    @tailrec
    def readSlice(commander: EntityRef[B], from: Int, to: Int, lazyList: LazyList[A]): LazyList[A] = {

      val slice: Seq[A] = Await.result(commander.ask(commandGenerator(from, to)), Duration.Inf)

      if (slice.isEmpty) lazyList
      else readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  final val serviceErrorCodePrefix: String = "011"
  final val defaultProblemType: String     = "about:blank"

  def problemOf(httpError: StatusCode, error: ComponentError, defaultMessage: String = "Unknown error"): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultMessage)
        )
      )
    )
}
