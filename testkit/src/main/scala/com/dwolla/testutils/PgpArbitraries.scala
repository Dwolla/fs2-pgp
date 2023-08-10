package com.dwolla.testutils

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.BouncyCastleResource
import com.dwolla.testutils.PgpArbitraries.KeySize
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._

import java.security.KeyPairGenerator
import java.util.Date

trait PgpArbitraries extends PgpArbitrariesPlatform {
  type KeySize = Int 

  override implicit def arbPgpPublicKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPublicKey))
  }

  override implicit def arbPgpPrivateKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPrivateKey))
  }

  override def genStrongKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    for {
      keySize <- Gen.oneOf[KeySize](2048, 4096)
      keyPair <- genKeyPair[F](keySize)
    } yield keyPair

  override def arbWeakKeyPair[F[_] : Sync]: Arbitrary[Resource[F, PGPKeyPair]] =
    Arbitrary(genWeakKeyPair)

  override def genWeakKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    genKeyPair[F](512)

  override def genKeyPair[F[_] : Sync](keySize: KeySize): Gen[Resource[F, PGPKeyPair]] =
    BouncyCastleResource[F]
      .evalMap { _ =>
        for {
          generator <- Sync[F].blocking {
            val instance = KeyPairGenerator.getInstance("RSA", "BC")
            instance.initialize(keySize)
            instance
          }
          pair <- Sync[F].delay {
            generator.generateKeyPair()
          }
          now <- Clock[F].realTime.map(_.toMillis).map(new Date(_))
          pgpPair <- MonadThrow[F].catchNonFatal {
            new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
          }
        } yield pgpPair
      }
}

object PgpArbitraries extends PgpArbitraries

// only kept to maintain binary compatibility
trait PgpArbitrariesPlatform {
  private[testutils] def arbPgpPublicKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = PgpArbitraries.arbPgpPublicKey
  private[testutils] def arbPgpPrivateKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = PgpArbitraries.arbPgpPrivateKey
  private[testutils] def genStrongKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] = PgpArbitraries.genStrongKeyPair
  private[testutils] def arbWeakKeyPair[F[_] : Sync]: Arbitrary[Resource[F, PGPKeyPair]] = PgpArbitraries.arbWeakKeyPair
  private[testutils] def genWeakKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] = PgpArbitraries.genWeakKeyPair
  private[testutils] def genKeyPair[F[_] : Sync](keySize: KeySize): Gen[Resource[F, PGPKeyPair]] = PgpArbitraries.genKeyPair(keySize)
}
