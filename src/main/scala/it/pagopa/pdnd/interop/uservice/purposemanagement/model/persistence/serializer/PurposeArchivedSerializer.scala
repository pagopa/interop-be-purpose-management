package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.PurposeArchived
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeArchivedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100004

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeDeactivatedManifest: String = classOf[PurposeArchived].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeArchived =>
      serialize(event, PurposeDeactivatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeDeactivatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeDeactivatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
