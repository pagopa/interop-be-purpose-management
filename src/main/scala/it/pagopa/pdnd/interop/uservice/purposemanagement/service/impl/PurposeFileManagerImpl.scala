package it.pagopa.pdnd.interop.uservice.purposemanagement.service.impl

import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.pdnd.interop.commons.files.service.FileManager
import it.pagopa.pdnd.interop.uservice.purposemanagement.common.system.ApplicationConfiguration
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.PurposeVersionDocument
import it.pagopa.pdnd.interop.uservice.purposemanagement.service.PurposeFileManager

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class PurposeFileManagerImpl(fileManager: FileManager) extends PurposeFileManager {
  override def store(id: UUID, timestamp: OffsetDateTime, fileParts: (FileInfo, File))(implicit
    ec: ExecutionContext
  ): Future[PurposeVersionDocument] = {
    fileManager
      .store(ApplicationConfiguration.storageContainer)(id, fileParts)
      .map(filePath =>
        PurposeVersionDocument(
          id = id,
          contentType = fileParts._1.getContentType.toString(),
          path = filePath,
          createdAt = timestamp
        )
      )
  }
}
