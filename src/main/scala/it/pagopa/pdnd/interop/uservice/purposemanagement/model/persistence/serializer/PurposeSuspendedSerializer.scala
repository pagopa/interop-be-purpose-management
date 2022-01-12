package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.PurposeSuspended
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeSuspendedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100003

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeSuspendedManifest: String = classOf[PurposeSuspended].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeSuspended =>
      serialize(event, PurposeSuspendedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeSuspendedManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeSuspendedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
