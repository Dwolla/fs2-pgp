package com.dwolla.security.crypto

import scala.util.control.NoStackTrace

class KeyRingMissingKeyException private (expectedKeyId: Option[Long])
  extends RuntimeException(s"Cannot decrypt message with the passed keyring because ${expectedKeyId.fold("it does not contain a compatible key and the message recipient is hidden")(id => s"it requires key $id, but the ring does not contain that key")}")
    with NoStackTrace

object KeyRingMissingKeyException {
  def apply(expectedKeyId: Option[Long]) = new KeyRingMissingKeyException(expectedKeyId)
}

class KeyMismatchException private (expectedKeyId: Option[Long], val actualKeyId: Long)
  extends RuntimeException(s"Cannot decrypt message with key $actualKeyId${expectedKeyId.fold(". (The message recipient is hidden.)")(id => s" because it requires key $id")}")
    with NoStackTrace

object KeyMismatchException {
  def apply(expectedKeyId: Option[Long], actualKeyId: Long) =
    new KeyMismatchException(expectedKeyId, actualKeyId)
}

object EncryptionTypeError
  extends RuntimeException("encrypted data was not PGPPublicKeyEncryptedData")
    with NoStackTrace
