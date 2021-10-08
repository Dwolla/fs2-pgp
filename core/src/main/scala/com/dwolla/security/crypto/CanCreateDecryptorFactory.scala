package com.dwolla.security.crypto

import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.bc.{BcPBESecretKeyDecryptorBuilder, BcPGPDigestCalculatorProvider, BcPublicKeyDataDecryptorFactory}
import org.bouncycastle.openpgp.{PGPPrivateKey, PGPSecretKey, PGPSecretKeyRing, PGPSecretKeyRingCollection}
import cats.effect.Sync

trait CanCreateDecryptorFactory[F[_], A] {
  def publicKeyDataDecryptorFactory(input: A, keyId: Long): F[PublicKeyDataDecryptorFactory]
}

class BlockingCanCreateDecryptorFactories[F[_] : Sync : ContextShift](blocker: Blocker) {
  private def secretKeyInstance(input: Option[PGPSecretKey],
                                keyId: Long,
                                passphrase: Array[Char]): F[PublicKeyDataDecryptorFactory] =
    OptionT.fromOption[F](input)
      .semiflatMap { secretKey =>
        Sync[F].blocking {
          val digestCalculatorProvider = new BcPGPDigestCalculatorProvider()
          val decryptor = new BcPBESecretKeyDecryptorBuilder(digestCalculatorProvider).build(passphrase)
          val key = secretKey.extractPrivateKey(decryptor)
          new BcPublicKeyDataDecryptorFactory(key)
        }
      }
      .getOrElseF(KeyRingMissingKeyException(keyId).raiseError[F, PublicKeyDataDecryptorFactory])

  implicit def PGPSecretKeyRingCollectionInstance: CanCreateDecryptorFactory[F, (PGPSecretKeyRingCollection, Array[Char])] =
    (input: (PGPSecretKeyRingCollection, Array[Char]), keyId: Long) =>
      secretKeyInstance(Option(input._1.getSecretKey(keyId)), keyId, input._2)

  implicit def PGPSecretKeyRingInstance: CanCreateDecryptorFactory[F, (PGPSecretKeyRing, Array[Char])] =
    (input: (PGPSecretKeyRing, Array[Char]), keyId: Long) =>
      secretKeyInstance(Option(input._1.getSecretKey(keyId)), keyId, input._2)

  implicit def PGPPrivateKeyInstance: CanCreateDecryptorFactory[F, PGPPrivateKey] =
    (input: PGPPrivateKey, keyId) =>
      if (input.getKeyID != keyId) KeyMismatchException(input.getKeyID, keyId).raiseError[F, PublicKeyDataDecryptorFactory]
      else Sync[F].blocking(new BcPublicKeyDataDecryptorFactory(input))
}

object CanCreateDecryptorFactory {
  def apply[F[_], A](implicit DFF: CanCreateDecryptorFactory[F, A]): CanCreateDecryptorFactory[F, A] = DFF

  def blockingInstances[F[_] : Sync : ContextShift] =
    new BlockingCanCreateDecryptorFactories[F](blocker)
}
