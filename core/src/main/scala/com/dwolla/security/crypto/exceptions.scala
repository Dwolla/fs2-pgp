package com.dwolla.security.crypto

case class KeyRingMissingKeyException(expectedKeyId: Long)
  extends RuntimeException(s"Cannot decrypt message with the passed keyring because it requires key $expectedKeyId, but the ring does not contain that key", null, true, false)

case class KeyMismatchException(expectedKeyId: Long, actualKeyId: Long)
  extends RuntimeException(s"Cannot decrypt message with key $actualKeyId because it requires key $expectedKeyId", null, true, false)

case object EncryptionTypeError extends RuntimeException("encrypted data was not PGPPublicKeyEncryptedData", null, true, false)
