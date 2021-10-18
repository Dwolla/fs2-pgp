package com.dwolla.testutils

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.BouncyCastleResource
import com.dwolla.testutils.PgpArbitraries.KeySize
import eu.timepit.refined.auto._
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, _}

import java.security.KeyPairGenerator
import java.util.Date
import scala.concurrent.duration.MILLISECONDS

trait PgpArbitrariesPlatform {
  implicit def arbPgpPublicKey[F[_] : Applicative](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPublicKey))
  }

  implicit def arbPgpPrivateKey[F[_] : Applicative](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPrivateKey))
  }

  def genStrongKeyPair[F[_] : Sync : ContextShift : Clock](blocker: Blocker): Gen[Resource[F, PGPKeyPair]] =
    for {
      keySize <- Gen.oneOf[KeySize](2048, 4096)
      keyPair <- genKeyPair[F](blocker, keySize)
    } yield keyPair

  def arbWeakKeyPair[F[_] : Sync : ContextShift : Clock](blocker: Blocker): Arbitrary[Resource[F, PGPKeyPair]] =
    Arbitrary(genWeakKeyPair(blocker))

  def genWeakKeyPair[F[_] : Sync : ContextShift : Clock](blocker: Blocker): Gen[Resource[F, PGPKeyPair]] =
    genKeyPair[F](blocker, 512)

  def genKeyPair[F[_] : Sync : ContextShift : Clock](blocker: Blocker,
                                                     keySize: KeySize): Gen[Resource[F, PGPKeyPair]] =
    BouncyCastleResource[F](blocker)
      .evalMap { _ =>
        for {
          generator <- blocker.delay {
            val instance = KeyPairGenerator.getInstance("RSA", "BC")
            instance.initialize(keySize.value)
            instance
          }
          pair <- Sync[F].delay {
            generator.generateKeyPair()
          }
          now <- Clock[F].realTime(MILLISECONDS).map(new Date(_))
          pgpPair <- MonadThrow[F].catchNonFatal {
            new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
          }
        } yield pgpPair
      }

}
