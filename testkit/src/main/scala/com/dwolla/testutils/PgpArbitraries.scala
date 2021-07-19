package com.dwolla.testutils

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.BouncyCastleResource
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.predicates.all._
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, _}

import java.security.KeyPairGenerator
import java.util.Date
import scala.concurrent.duration.MILLISECONDS

trait PgpArbitraries {
  type KeySizePred = GreaterEqual[W.`384`.T]
  type KeySize = Int Refined KeySizePred

  def arbStrongKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = Arbitrary {
    for {
      keySize <- Gen.oneOf[KeySize](2048, 4096)
      keyPair <- arbKeyPair[F](keySize).arbitrary
    } yield keyPair
  }

  def arbWeakKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] =
    arbKeyPair[F](512)

  def arbKeyPair[F[_] : Sync : ContextShift : Clock](keySize: KeySize): Arbitrary[Resource[F, PGPKeyPair]] = Arbitrary {
    Blocker[F]
      .flatMap(BouncyCastleResource[F](_, removeOnClose = false))
      .evalMap { _ =>
        for {
          pair <- Sync[F].delay {
            val generator = KeyPairGenerator.getInstance("RSA", "BC")
            generator.initialize(keySize.value)
            generator.generateKeyPair()
          }
          now <- Clock[F].realTime(MILLISECONDS).map(new Date(_))
          pgpPair <- Sync[F].delay {
            new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
          }
        } yield pgpPair
      }
  }

  implicit def arbPgpPublicKey[F[_] : Applicative](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPublicKey))
  }

  implicit def arbPgpPrivateKey[F[_] : Applicative](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbitrary[Resource[F, PGPKeyPair]].map(_.map(_.getPrivateKey))
  }
}

object PgpArbitraries extends PgpArbitraries
