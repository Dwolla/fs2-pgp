package com.dwolla.security.crypto

import org.bouncycastle.openpgp._

trait PGPKeyAlg[F[_]] {
  def readPublicKey(key: String): F[PGPPublicKey]
  def readPrivateKey(key: String, passphrase: Array[Char] = Array.empty[Char]): F[PGPPrivateKey]
  def readSecretKeyCollection(keys: String): F[PGPSecretKeyRingCollection]
}

object PGPKeyAlg extends PGPKeyAlgPlatform
