package com.dwolla.security.crypto

import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.bc.{BcPBESecretKeyDecryptorBuilder, BcPGPDigestCalculatorProvider, BcPublicKeyDataDecryptorFactory}
import org.bouncycastle.openpgp.{PGPPrivateKey, PGPSecretKey, PGPSecretKeyRing, PGPSecretKeyRingCollection}

import scala.language.reflectiveCalls

trait CanCreateDecryptorFactory[F[_], A] {
  def publicKeyDataDecryptorFactory(input: A, keyId: Long, passphrase: Array[Char]): F[PublicKeyDataDecryptorFactory]
}

object CanCreateDecryptorFactory {
  def apply[F[_], A](implicit DFF: CanCreateDecryptorFactory[F, A]): CanCreateDecryptorFactory[F, A] = DFF

  implicit def PGPSecretKeyRingCollectionInstance[F[_] : Sync]: CanCreateDecryptorFactory[F, PGPSecretKeyRingCollection] =
    reflectiveDecryptorFactoryFactoryInstance(_, _, _)

  implicit def PGPSecretKeyRingInstance[F[_] : Sync]: CanCreateDecryptorFactory[F, PGPSecretKeyRing] =
    reflectiveDecryptorFactoryFactoryInstance(_, _, _)

  private def reflectiveDecryptorFactoryFactoryInstance[F[_] : Sync, A <: {def getSecretKey(keyId: Long): PGPSecretKey}](input: A,
                                                                                                                         keyId: Long,
                                                                                                                         passphrase: Array[Char]): F[PublicKeyDataDecryptorFactory] =
    OptionT.fromOption[F](Option(input.getSecretKey(keyId)))
      .semiflatMap { secretKey =>
        Sync[F].delay {
          val digestCalculatorProvider = new BcPGPDigestCalculatorProvider()
          val decryptor = new BcPBESecretKeyDecryptorBuilder(digestCalculatorProvider).build(passphrase)
          val key = secretKey.extractPrivateKey(decryptor)
          new BcPublicKeyDataDecryptorFactory(key)
        }
      }
      .getOrElseF(KeyRingMissingKeyException(keyId).raiseError[F, PublicKeyDataDecryptorFactory])

  implicit def PGPPrivateKeyInstance[F[_] : Sync]: CanCreateDecryptorFactory[F, PGPPrivateKey] =
    (input: PGPPrivateKey, keyId, _) =>
      if (input.getKeyID != keyId) KeyMismatchException(input.getKeyID, keyId).raiseError[F, PublicKeyDataDecryptorFactory]
      else Sync[F].delay(new BcPublicKeyDataDecryptorFactory(input))
}
