package com.dwolla.security.crypto

import cats.syntax.all._

import scala.annotation.nowarn
import scala.runtime.{AbstractFunction1, AbstractFunction2}
import scala.util.control.NoStackTrace

class KeyRingMissingKeyException(expectedKeyId: Option[Long])
  extends RuntimeException(s"Cannot decrypt message with the passed keyring because ${expectedKeyId.fold("it does not contain a compatible key and the message recipient is hidden")(id => s"it requires key $id, but the ring does not contain that key")}")
    with NoStackTrace
    with Product
    with Equals
    with Serializable {
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def this(keyId: Long) = this(keyId.some)

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productArity: Int = 1

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productElement(n: Int): Any = if (n == 0) expectedKeyId.getOrElse(0) else throw new IndexOutOfBoundsException()

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def canEqual(that: Any): Boolean = that.isInstanceOf[KeyRingMissingKeyException]

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def expectedKeyId(): Long = expectedKeyId.getOrElse(0)

  @nowarn
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def copy(keyId: Long = expectedKeyId()): KeyRingMissingKeyException = KeyRingMissingKeyException(keyId)
}

object KeyRingMissingKeyException extends AbstractFunction1[Long, KeyRingMissingKeyException] {
  def apply(expectedKeyId: Option[Long]) = new KeyRingMissingKeyException(expectedKeyId)
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def apply(keyId: Long): KeyRingMissingKeyException = KeyRingMissingKeyException(keyId.some)

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def unapply(arg: KeyRingMissingKeyException): Option[Long] =
    arg.expectedKeyId().some
}

class KeyMismatchException(expectedKeyId: Option[Long], val actualKeyId: Long)
  extends RuntimeException(s"Cannot decrypt message with key $actualKeyId${expectedKeyId.fold(". (The message recipient is hidden.)")(id => s" because it requires key $id")}")
    with NoStackTrace
    with Product
    with Equals
    with Serializable {
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def this(expectedKeyId: Long, actualKeyId: Long) = this(Option(expectedKeyId).filter(_ == 0), actualKeyId)

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productArity: Int = 2

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productElement(n: Int): Any = n match {
    case 0 => expectedKeyId.getOrElse(0)
    case 1 => actualKeyId
    case _ => throw new IndexOutOfBoundsException()
  }

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def canEqual(that: Any): Boolean = that.isInstanceOf[KeyMismatchException]

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def expectedKeyId(): Long = expectedKeyId.getOrElse(0)

  @nowarn
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def copy(expectedKeyId: Long = this.expectedKeyId(), actualKeyId: Long = actualKeyId) =
    new KeyMismatchException(Option(expectedKeyId).filter(_ == 0), actualKeyId)
}

object KeyMismatchException extends AbstractFunction2[Long, Long, KeyMismatchException] {
  @deprecated("only maintained for bincompat reasons", "0.4.0")
  def unapply(arg: KeyMismatchException): Option[(Long, Long)] =
    (arg.expectedKeyId(), arg.actualKeyId).some

  def apply(expectedKeyId: Option[Long], actualKeyId: Long) = new KeyMismatchException(expectedKeyId, actualKeyId)

  override def apply(v1: Long, v2: Long): KeyMismatchException =
    KeyMismatchException(v1.some, v2)

}

object EncryptionTypeError
  extends RuntimeException("encrypted data was not PGPPublicKeyEncryptedData")
    with NoStackTrace
    with Product
    with Equals
    with Serializable {

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productArity: Int = 0

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def productElement(n: Int): Any = throw new IndexOutOfBoundsException()

  @deprecated("only maintained for bincompat reasons", "0.4.0")
  override def canEqual(that: Any): Boolean = this == EncryptionTypeError

  override def hashCode(): Int = super.hashCode()
}
