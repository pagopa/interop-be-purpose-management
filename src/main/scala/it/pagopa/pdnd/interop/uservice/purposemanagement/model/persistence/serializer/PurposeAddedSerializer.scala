package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.PurposeAdded
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100000

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeAddedManifest: String = classOf[PurposeAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeAdded =>
      serialize(event, PurposeAddedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
