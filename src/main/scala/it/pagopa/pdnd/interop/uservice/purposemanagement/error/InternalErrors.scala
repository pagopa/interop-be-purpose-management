package it.pagopa.pdnd.interop.uservice.purposemanagement.error

object InternalErrors {
  final case class PurposeNotFound(purposeId: String) extends Throwable(s"Purpose $purposeId not found")
  final case class PurposeVersionNotFound(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId not found for purpose $purposeId")
  final case class PurposeVersionNotInDraft(purposeId: String, versionId: String)
      extends Throwable(s"Version $versionId of purpose $purposeId is not in DRAFT")
}
