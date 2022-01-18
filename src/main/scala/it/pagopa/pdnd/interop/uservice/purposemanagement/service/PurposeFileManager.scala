package it.pagopa.pdnd.interop.uservice.purposemanagement.service

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.commons.files.service.FileManager
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersionDocument

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PurposeFileManager {
  val fileManager: FileManager

  def store(id: UUID, timestamp: OffsetDateTime, fileParts: (FileInfo, File))(implicit
    ec: ExecutionContext
  ): Future[PurposeVersionDocument]

}
