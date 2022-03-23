package it.pagopa.interop.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.purposemanagement.model.persistence.PurposeVersionActivated
import it.pagopa.interop.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeVersionActivatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100002

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val className: String = classOf[PurposeVersionActivated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeVersionActivated =>
      serialize(event, className, currentVersion)
    case _                              =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[${manifest(o)}]], currentVersion: [[$currentVersion]] "
      )
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case `className` :: `version1` :: Nil =>
      deserialize(v1.events.PurposeVersionActivatedV1, bytes, manifest, currentVersion)
    case _                                =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
