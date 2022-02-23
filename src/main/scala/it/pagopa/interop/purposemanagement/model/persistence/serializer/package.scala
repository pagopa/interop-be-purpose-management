package it.pagopa.interop.purposemanagement.model.persistence

import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

import java.io.NotSerializableException

package object serializer {
  def serialize[A, B <: GeneratedMessage](a: A, manifest: String, currentVersion: String)(implicit
    ps: PersistEventSerializer[A, B]
  ): Array[Byte] =
    PersistEventSerializer
      .to(a)
      .getOrElse(
        throw new NotSerializableException(
          s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
        )
      )
      .toByteArray

  def deserialize[A <: GeneratedMessage, P <: Persistable](
    msg: GeneratedMessageCompanion[A],
    bytes: Array[Byte],
    manifest: String,
    currentVersion: String
  )(implicit ec: PersistEventDeserializer[A, P]): AnyRef =
    PersistEventDeserializer
      .from(msg.parseFrom(bytes))
      .getOrElse(
        throw new NotSerializableException(
          s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
        )
      )
}
