package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.PurposeVersionWaitingForApproval
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeVersionWaitingForApprovalSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100006

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeVersionWaitingForApprovalManifest: String = classOf[PurposeVersionWaitingForApproval].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeVersionWaitingForApproval =>
      serialize(event, PurposeVersionWaitingForApprovalManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeVersionWaitingForApprovalManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeVersionWaitingForApprovalV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
