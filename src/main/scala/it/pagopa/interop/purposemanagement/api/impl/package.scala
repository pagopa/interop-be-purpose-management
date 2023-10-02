package it.pagopa.interop.purposemanagement.api

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.util.Timeout
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.purposemanagement.model._
import it.pagopa.interop.purposemanagement.model.persistence.Command
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val riskAnalysisSingleAnswerFormat: RootJsonFormat[RiskAnalysisSingleAnswer] =
    jsonFormat3(RiskAnalysisSingleAnswer)
  implicit val riskAnalysisMultiAnswerFormat: RootJsonFormat[RiskAnalysisMultiAnswer]   =
    jsonFormat3(RiskAnalysisMultiAnswer)
  implicit val riskAnalysisFormFormat: RootJsonFormat[RiskAnalysisForm]                 = jsonFormat5(RiskAnalysisForm)
  implicit val riskAnalysisSingleAnswerSeedFormat: RootJsonFormat[RiskAnalysisSingleAnswerSeed] =
    jsonFormat2(RiskAnalysisSingleAnswerSeed)
  implicit val riskAnalysisMultiAnswerSeedFormat: RootJsonFormat[RiskAnalysisMultiAnswerSeed]   =
    jsonFormat2(RiskAnalysisMultiAnswerSeed)
  implicit val riskAnalysisFormSeedFormat: RootJsonFormat[RiskAnalysisFormSeed] = jsonFormat4(RiskAnalysisFormSeed)
  implicit val purposeSeedFormat: RootJsonFormat[PurposeSeed]                   = jsonFormat8(PurposeSeed)
  implicit val PurposeUpdateContentFormat: RootJsonFormat[PurposeUpdateContent] = jsonFormat6(PurposeUpdateContent)
  implicit val versionDocumentFormat: RootJsonFormat[PurposeVersionDocument]    = jsonFormat4(PurposeVersionDocument)
  implicit val purposeVersionSeedFormat: RootJsonFormat[PurposeVersionSeed]     = jsonFormat2(PurposeVersionSeed)
  implicit val waitingForApprovalPurposeVersionUpdateFormat
    : RootJsonFormat[WaitingForApprovalPurposeVersionUpdateContent] =
    jsonFormat1(WaitingForApprovalPurposeVersionUpdateContent)
  implicit val purposeVersionFormat: RootJsonFormat[PurposeVersion]             = jsonFormat9(PurposeVersion)
  implicit val purposeFormat: RootJsonFormat[Purpose]                           = jsonFormat13(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes]                         = jsonFormat1(Purposes)
  implicit val stateChangeDetailsFormat: RootJsonFormat[StateChangeDetails]     = jsonFormat2(StateChangeDetails)
  implicit val problemErrorFormat: RootJsonFormat[ProblemError]                 = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                           = jsonFormat6(Problem)
  implicit val activatePurposeVersionPayloadFormat: RootJsonFormat[ActivatePurposeVersionPayload] =
    jsonFormat2(ActivatePurposeVersionPayload)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

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

}
