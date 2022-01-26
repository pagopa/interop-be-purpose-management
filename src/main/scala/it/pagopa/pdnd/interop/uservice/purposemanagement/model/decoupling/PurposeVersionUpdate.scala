package it.pagopa.pdnd.interop.uservice.purposemanagement.model.decoupling

import it.pagopa.pdnd.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.{PurposeVersionDocument, PurposeVersionUpdateContent}

import java.time.OffsetDateTime

final case class PurposeVersionUpdate(riskAnalysis: Option[PurposeVersionDocument], timestamp: OffsetDateTime)

object PurposeVersionUpdate {
  def fromApi(update: PurposeVersionUpdateContent, dateTimeSupplier: OffsetDateTimeSupplier): PurposeVersionUpdate =
    PurposeVersionUpdate(riskAnalysis = update.riskAnalysis, timestamp = dateTimeSupplier.get)
}
