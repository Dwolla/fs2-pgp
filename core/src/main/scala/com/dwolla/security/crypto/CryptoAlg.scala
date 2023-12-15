package com.dwolla.security.crypto

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import fs2._
import org.bouncycastle.openpgp._
import org.typelevel.log4cats.{LoggerFactory, LoggerName}

trait CryptoAlg[F[_]]
  extends Encrypt[F]
    with Decrypt[F]
    with Armor[F]

object CryptoAlg {
  def resource[F[_] : Async : LoggerFactory]: Resource[F, CryptoAlg[F]] =
    BouncyCastleResource[F].evalMap { implicit ev =>
      CryptoAlg[F]
    }

  def apply[F[_] : Async : LoggerFactory](implicit ev: BouncyCastleResource): F[CryptoAlg[F]] =
    for {
      d <- LoggerFactory[F].create(LoggerName("com.dwolla.security.crypto.Decrypt")).map(implicit l => Decrypt[F])
      e <- LoggerFactory[F].create(LoggerName("com.dwolla.security.crypto.Encrypt")).map(implicit l => Encrypt[F])
      a = Armor[F]
    } yield new CryptoAlg[F] {
      override def encrypt(keys: NonEmptyList[PGPPublicKey], config: EncryptionConfig): Pipe[F, Byte, Byte] =
        e.encrypt(keys, config)

      override def armor(chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        a.armor(chunkSize)

      override def decrypt(key: PGPPrivateKey, chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        d.decrypt(key, chunkSize)

      override def decrypt(keyring: PGPSecretKeyRing, passphrase: Array[Char], chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        d.decrypt(keyring, passphrase, chunkSize)

      override def decrypt(keyring: PGPSecretKeyRingCollection, passphrase: Array[Char], chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        d.decrypt(keyring, passphrase, chunkSize)
    }
}
