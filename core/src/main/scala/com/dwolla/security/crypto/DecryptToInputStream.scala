package com.dwolla.security.crypto

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.bc.{
  BcPBESecretKeyDecryptorBuilder,
  BcPGPDigestCalculatorProvider,
  BcPublicKeyDataDecryptorFactory
}
import org.typelevel.log4cats.{Logger, LoggerFactory, LoggerName}

import java.io.InputStream
import scala.jdk.CollectionConverters._

private[crypto] sealed trait DecryptToInputStream[F[_], A] {
  def decryptToInputStream(input: A, maybeKeyId: Option[Long])(
      pbed: PGPPublicKeyEncryptedData
  ): F[InputStream]

  def hasKeyId(input: A, id: Long): Boolean
}

private[crypto] object DecryptToInputStream {
  @inline final def apply[F[_], A](implicit
      DTIS: DecryptToInputStream[F, A]
  ): DTIS.type = DTIS

  private def attemptDecrypt[F[_]: Sync](
      pbed: PGPPublicKeyEncryptedData,
      factory: BcPublicKeyDataDecryptorFactory
  ): F[InputStream] =
    Sync[F].blocking {
      pbed.getDataStream(factory)
    }

  /** Tries to decrypt the `PGPPublicKeyEncryptedData` using each secret key in the given list of keys.
    * The first key that doesn't throw an exception when doing `pbed.getDataStream` will be the one
    * whose `InputStream` is read downstream. Once it finds a key that works, it will stop trying
    * any subsequent keys.
    */
  private def decryptWithKeys[F[_]: Sync: Logger](
      input: List[PGPSecretKey],
      passphrase: Array[Char],
      pbed: PGPPublicKeyEncryptedData,
      keyId: Option[Long]
  ): F[InputStream] =
    Stream
      .emits(input)
      .evalMap { secretKey =>
        Sync[F].blocking {
          val digestCalculatorProvider = new BcPGPDigestCalculatorProvider()
          val decryptor = new BcPBESecretKeyDecryptorBuilder(
            digestCalculatorProvider
          ).build(passphrase)
          val key = secretKey.extractPrivateKey(decryptor)
          secretKey -> new BcPublicKeyDataDecryptorFactory(key)
        }
      }
      .evalMap { case (key, decryptorFactory) =>
        attemptDecrypt(pbed, decryptorFactory)
          .map(_.some)
          .handleErrorWith {
            case t if keyId.contains(key.getKeyID) =>
              Logger[F]
                .trace(t)(
                  s"an error occurred decrypting with what appears to be the correct key ID (${key.getKeyID}); ignoring and attempting to continue"
                )
                .as(None)
            case _ =>
              none.pure[F]
          }
      }
      .unNone
      .head
      .compile
      .lastOrError
      .adaptErr { case _: NoSuchElementException =>
        KeyRingMissingKeyException(keyId)
      }

  implicit def PGPSecretKeyRingCollectionInstance[F[_]: Sync: LoggerFactory]
      : DecryptToInputStream[F, (PGPSecretKeyRingCollection, Array[Char])] =
    new DecryptToInputStream[F, (PGPSecretKeyRingCollection, Array[Char])] {

      override def hasKeyId(
          input: (PGPSecretKeyRingCollection, Array[Char]),
          id: Long
      ): Boolean =
        input._1.contains(id)

      override def decryptToInputStream(
          input: (PGPSecretKeyRingCollection, Array[Char]),
          maybeKeyId: Option[Long]
      )(pbed: PGPPublicKeyEncryptedData): F[InputStream] =
        LoggerFactory[F]
          .create(
            LoggerName(
              "com.dwolla.security.crypto.DecryptToInputStream.PGPSecretKeyRingCollectionInstance"
            )
          )
          .flatMap { implicit logger =>
            maybeKeyId.toOptionT
              .flatMapF { keyId =>
                ApplicativeThrow[F].catchNonFatal {
                  Option(input._1.getSecretKey(keyId))
                }
              }
              .map(_.pure[List])
              .getOrElse(
                input._1.getKeyRings.asScala.toList
                  .flatMap(_.getSecretKeys.asScala)
              )
              .flatMap(decryptWithKeys(_, input._2, pbed, maybeKeyId))
          }
    }

  implicit def PGPSecretKeyRingInstance[F[_]: Sync: LoggerFactory]
      : DecryptToInputStream[F, (PGPSecretKeyRing, Array[Char])] =
    new DecryptToInputStream[F, (PGPSecretKeyRing, Array[Char])] {
      override def hasKeyId(
          input: (PGPSecretKeyRing, Array[Char]),
          id: Long
      ): Boolean =
        input._1.getSecretKeys.asScala
          .exists(_.getKeyID == id)

      override def decryptToInputStream(
          input: (PGPSecretKeyRing, Array[Char]),
          maybeKeyId: Option[Long]
      )(pbed: PGPPublicKeyEncryptedData): F[InputStream] = {
        val keys = maybeKeyId.fold(input._1.getSecretKeys.asScala.toList) {
          keyId =>
            Option(input._1.getSecretKey(keyId)).toList
        }

        LoggerFactory[F]
          .create(
            LoggerName(
              "com.dwolla.security.crypto.DecryptToInputStream.PGPSecretKeyRingCollectionInstance"
            )
          )
          .flatMap { implicit logger =>
            decryptWithKeys(keys, input._2, pbed, maybeKeyId)
          }
      }
    }

  implicit def PGPPrivateKeyInstance[F[_]: Sync]
      : DecryptToInputStream[F, PGPPrivateKey] =
    new DecryptToInputStream[F, PGPPrivateKey] {
      override def hasKeyId(input: PGPPrivateKey, id: Long): Boolean =
        input.getKeyID == id

      override def decryptToInputStream(
          input: PGPPrivateKey,
          maybeKeyId: Option[Long]
      )(pbed: PGPPublicKeyEncryptedData): F[InputStream] =
        if (maybeKeyId.exists(_ != input.getKeyID))
          KeyMismatchException(maybeKeyId, input.getKeyID).raiseError
        else
          Sync[F]
            .blocking(new BcPublicKeyDataDecryptorFactory(input))
            .flatMap(attemptDecrypt(pbed, _))
    }

  implicit def toPGPPublicKeyEncryptedDataOps(
      pbed: PGPPublicKeyEncryptedData
  ): PGPPublicKeyEncryptedDataOps = new PGPPublicKeyEncryptedDataOps(pbed)
}

class PGPPublicKeyEncryptedDataOps(val pbed: PGPPublicKeyEncryptedData)
    extends AnyVal {
  def decryptToInputStream[F[_], A](input: A, maybeKeyId: Option[Long])(implicit
      D: DecryptToInputStream[F, A]
  ): F[InputStream] =
    DecryptToInputStream[F, A].decryptToInputStream(input, maybeKeyId)(pbed)
}
