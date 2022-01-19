package it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.RiskAnalysisAdded
import it.pagopa.pdnd.interop.uservice.purposemanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class RiskAnalysisAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100005

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val RiskAnalysisAddedManifest: String = classOf[RiskAnalysisAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: RiskAnalysisAdded =>
      serialize(event, RiskAnalysisAddedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case RiskAnalysisAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.RiskAnalysisAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
