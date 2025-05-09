package com.dwolla.testutils

import cats.*
import cats.effect.*
import cats.syntax.all.*
import com.dwolla.security.crypto.BouncyCastleResource
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.*
import org.scalacheck.Arbitrary.arbitrary

import java.security.KeyPairGenerator
import java.util.Date

trait PgpArbitraries extends PgpArbitrariesPlatform {
  type KeySize = Int Refined KeySizePred
  object KeySize extends RefinedTypeOps.Numeric[KeySize, Int]

  implicit def arbPgpPublicKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPublicKey))
  }

  implicit def arbPgpPrivateKey[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPrivateKey))
  }

  def genStrongKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    for {
      keySize <- Gen.oneOf[KeySize](KeySize2048, KeySize4096)
      keyPair <- genKeyPair[F](keySize)
    } yield keyPair

  def arbWeakKeyPair[F[_] : Sync]: Arbitrary[Resource[F, PGPKeyPair]] =
    Arbitrary(genWeakKeyPair)

  def genWeakKeyPair[F[_] : Sync]: Gen[Resource[F, PGPKeyPair]] =
    genKeyPair[F](KeySize512)

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
            // TODO the versionless `JcaPGPKeyPair` constructor is deprecated in BC1.80+
            new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
          }
        } yield pgpPair
      }
}

object PgpArbitraries extends PgpArbitraries
