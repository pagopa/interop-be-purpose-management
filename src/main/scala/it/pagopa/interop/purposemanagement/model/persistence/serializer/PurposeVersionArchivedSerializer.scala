package it.pagopa.interop.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.purposemanagement.model.persistence.PurposeVersionArchived
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeVersionArchivedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100004

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeVersionArchivedManifest: String = classOf[PurposeVersionArchived].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeVersionArchived =>
      serialize(event, PurposeVersionArchivedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeVersionArchivedManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeVersionArchivedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}