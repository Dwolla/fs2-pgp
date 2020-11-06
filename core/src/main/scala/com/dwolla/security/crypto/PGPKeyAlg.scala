package com.dwolla.security.crypto

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import cats._
import cats.effect._
import fs2._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.bc.{BcPBESecretKeyDecryptorBuilder, BcPGPDigestCalculatorProvider}
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

trait PGPKeyAlg[F[_]] {
  def readPublicKey(key: String): F[PGPPublicKey]
  def readPrivateKey(key: String, passphrase: Array[Char] = Array.empty[Char]): F[PGPPrivateKey]
}

object PGPKeyAlg {
  class PartiallyAppliedPGPKeyAlg[F[_]] private[PGPKeyAlg] () {
    def apply[G[_] : Stream.Compiler[*[_], F] : Sync : ContextShift](blocker: Blocker)
                                                                    (implicit F: MonadError[F, Throwable]): PGPKeyAlg[F] = new PGPKeyAlg[F] {
      import scala.jdk.CollectionConverters._

      override def readPublicKey(key: String): F[PGPPublicKey] =
        publicKeyStream(key)
          .filter(_.isEncryptionKey)
          .head
          .compile
          .lastOrError

      override def readPrivateKey(key: String,
                                  passphrase: Array[Char]): F[PGPPrivateKey] =
        secretKeyStream(key)
          .evalMap { secretKey =>
            blocker.delay {
              val digestCalculatorProvider = new BcPGPDigestCalculatorProvider()
              val decryptor = new BcPBESecretKeyDecryptorBuilder(digestCalculatorProvider).build(passphrase)
              secretKey.extractPrivateKey(decryptor)
            }
          }
          .map(Option(_))
          .unNone
          .head
          .compile
          .lastOrError

      private def keyBytes(armored: String): G[ByteArrayInputStream] =
        blocker.delay {
          new ByteArrayInputStream(armored.getBytes(Charset.forName("UTF-8")))
        }

      private def publicKeyStream(key: String): Stream[G, PGPPublicKey] =
        for {
          is <- Stream.eval(keyBytes(key))
          keyRingCollection <- Stream.eval(blocker.delay {
            new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(is), new JcaKeyFingerprintCalculator())
          })
          keyRing <- Stream.fromBlockingIterator(blocker, keyRingCollection.getKeyRings.asScala)
          key <- Stream.fromBlockingIterator(blocker, keyRing.getPublicKeys.asScala)
        } yield key

      private def secretKeyStream(key: String): Stream[G, PGPSecretKey] =
        for {
          is <- Stream.eval(keyBytes(key))
          keyRingCollection <- Stream.eval(blocker.delay {
            new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(is), new JcaKeyFingerprintCalculator())
          })
          keyRing <- Stream.fromBlockingIterator(blocker, keyRingCollection.getKeyRings.asScala)
          secretKey <- Stream.fromBlockingIterator(blocker, keyRing.getSecretKeys.asScala)
        } yield secretKey
    }
  }

  /**
   * Starts to build a PGPKeyAlg by fixing the higher-kinded output type param
   * that needs to be provided or inferred. The type provided here will be the
   * effect in which the return values from PGPKeyAlg's methods will operate.
   *
   * The second type provided to `PartiallyAppliedPGPKeyAlg.apply` will be
   * the main output type. It will typically be the same type provided here,
   * but other types are possible:
   * {{{
   * val key: String = "armored key"
   *
   * // second HKT inferred to be IO
   * val ioAlg: PGPKeyAlg[IO] = PGPKeyAlg[IO](blocker)
   * val readInIO: IO[PGPPublicKey] = ioAlg.readPublicKey(key)
   *
   * // second HKT inferred to be IO
   * val resourceAlg: PGPKeyAlg[Resource[IO, *]] = PGPKeyAlg[Resource[IO, *]](blocker)
   * val readInResource: Resource[IO, PGPPublicKey] = resourceAlg.readPublicKey(key)
   * }}}
   */
  def apply[F[_]]: PartiallyAppliedPGPKeyAlg[F] = new PartiallyAppliedPGPKeyAlg[F]
}
