package it.pagopa.interop.purposemanagement.model.persistence.serializer

trait PersistEventSerializer[A, B] {
  def to(a: A): Either[Throwable, B]
}

object PersistEventSerializer {
  def to[A, B](a: A)(implicit e: PersistEventSerializer[A, B]): Either[Throwable, B] = e.to(a)
}
