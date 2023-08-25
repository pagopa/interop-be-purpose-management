package it.pagopa.interop.purposemanagement.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}
import it.pagopa.interop.purposemanagement.error.PurposeManagementErrors._

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("011")

  def createPurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getPurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                   => success(s)
      case Failure(ex: PurposeNotFound) => notFound(ex, logMessage)
      case Failure(ex)                  => internalServerError(ex, logMessage)
    }

  def deletePurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: PurposeNotFound)         => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionsNotEmpty) => conflict(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def createPurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: PurposeNotFound)             => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionStateConflict) => conflict(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def deletePurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex: NotAllowedForPurposeVersionState) => conflict(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def activatePurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: NotAllowedForPurposeVersionState) => badRequest(ex, logMessage)
      case Failure(ex: PurposeMissingRiskAnalysis)       => badRequest(ex, logMessage)
      case Failure(ex: PurposeNotFound)                  => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def suspendPurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: NotAllowedForPurposeVersionState) => badRequest(ex, logMessage)
      case Failure(ex: PurposeNotFound)                  => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def waitForApprovalPurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: NotAllowedForPurposeVersionState) => badRequest(ex, logMessage)
      case Failure(ex: PurposeMissingRiskAnalysis)       => badRequest(ex, logMessage)
      case Failure(ex: PurposeNotFound)                  => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def archivePurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: NotAllowedForPurposeVersionState) => badRequest(ex, logMessage)
      case Failure(ex: PurposeNotFound)                  => notFound(ex, logMessage)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def getPurposesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def updatePurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: PurposeNotFound)                  => notFound(ex, logMessage)
      case Failure(ex: NotAllowedForPurposeVersionState) => conflict(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

  def updateWaitingForApprovalPurposeVersionResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: NotAllowedForPurposeVersionState) => badRequest(ex, logMessage)
      case Failure(ex: PurposeVersionNotFound)           => notFound(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

}
