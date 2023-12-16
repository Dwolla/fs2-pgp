package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import fs2._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.bc.{
  BcPBESecretKeyDecryptorBuilder,
  BcPGPDigestCalculatorProvider
}
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

trait PGPKeyAlg[F[_]] {
  def readPublicKey(key: String): F[PGPPublicKey]
  def readPrivateKey(
      key: String,
      passphrase: Array[Char] = Array.empty[Char]
  ): F[PGPPrivateKey]
  def readSecretKeyCollection(keys: String): F[PGPSecretKeyRingCollection]
}

object PGPKeyAlg {
  private val keyChunkSize: Int = 1

  def apply[F[_]: Async]: PGPKeyAlg[F] = new PGPKeyAlg[F] {
    import scala.jdk.CollectionConverters._

    override def readPublicKey(key: String): F[PGPPublicKey] =
      publicKeyStream(key)
        .filter(_.isEncryptionKey)
        .head
        .compile
        .lastOrError

    override def readPrivateKey(
        key: String,
        passphrase: Array[Char]
    ): F[PGPPrivateKey] =
      secretKeyStream(key)
        .evalMap { secretKey =>
          Sync[F].blocking {
            val digestCalculatorProvider = new BcPGPDigestCalculatorProvider()
            val decryptor = new BcPBESecretKeyDecryptorBuilder(
              digestCalculatorProvider
            ).build(passphrase)
            secretKey.extractPrivateKey(decryptor)
          }
        }
        .map(Option(_))
        .unNone
        .head
        .compile
        .lastOrError

    override def readSecretKeyCollection(
        keys: String
    ): F[PGPSecretKeyRingCollection] =
      Stream.eval(secretKeyRingCollection(keys)).compile.lastOrError

    private def keyBytes(armored: String): F[ByteArrayInputStream] =
      Sync[F].blocking {
        new ByteArrayInputStream(armored.getBytes(Charset.forName("UTF-8")))
      }

    private def publicKeyStream(key: String): Stream[F, PGPPublicKey] =
      for {
        is <- Stream.eval(keyBytes(key))
        keyRingCollection <- Stream.eval(Sync[F].blocking {
          new PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(is),
            new JcaKeyFingerprintCalculator()
          )
        })
        keyRing <- Stream.fromBlockingIterator[F](
          keyRingCollection.getKeyRings.asScala,
          keyChunkSize
        )
        key <- Stream
          .fromBlockingIterator[F](keyRing.getPublicKeys.asScala, keyChunkSize)
      } yield key

    private def secretKeyRingCollection(
        keys: String
    ): F[PGPSecretKeyRingCollection] =
      for {
        is <- keyBytes(keys)
        keyRingCollection <- Sync[F].blocking {
          new PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(is),
            new JcaKeyFingerprintCalculator()
          )
        }
      } yield keyRingCollection

    private def secretKeyStream(key: String): Stream[F, PGPSecretKey] =
      for {
        keyRingCollection <- Stream.eval(secretKeyRingCollection(key))
        keyRing <- Stream.fromBlockingIterator[F](
          keyRingCollection.getKeyRings.asScala,
          keyChunkSize
        )
        secretKey <- Stream
          .fromBlockingIterator[F](keyRing.getSecretKeys.asScala, keyChunkSize)
      } yield secretKey
  }
}
