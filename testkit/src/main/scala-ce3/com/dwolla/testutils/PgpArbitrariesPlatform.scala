package com.dwolla.testutils

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.BouncyCastleResource
import com.dwolla.testutils.PgpArbitraries._
import eu.timepit.refined.auto._
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, _}

import java.security.KeyPairGenerator
import java.util.Date

trait PgpArbitrariesPlatform {
  implicit def arbPgpPublicKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPublicKey))
  }

  implicit def arbPgpPrivateKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPrivateKey))
  }

  def genStrongKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    for {
      keySize <- Gen.oneOf[KeySize](2048, 4096)
      keyPair <- genKeyPair[F](keySize)
    } yield keyPair

  def arbWeakKeyPair[F[_] : Sync]: Arbitrary[Resource[F, PGPKeyPair]] =
    Arbitrary(genWeakKeyPair)

  def genWeakKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    genKeyPair[F](512)

  def genKeyPair[F[_] : Sync](keySize: KeySize): Gen[Resource[F, PGPKeyPair]] =
    BouncyCastleResource[F]
      .evalMap { _ =>
        for {
          generator <- Sync[F].blocking {
            val instance = KeyPairGenerator.getInstance("RSA", "BC")
            instance.initialize(keySize.value)
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
