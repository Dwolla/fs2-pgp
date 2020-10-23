package com.dwolla.testutils

import java.security.KeyPairGenerator
import java.util.Date

import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.BouncyCastleResource
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.scalacheck._

import scala.concurrent.duration.MILLISECONDS

trait PgpArbitraries {
  implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = Arbitrary {
    Gen.oneOf(2048, 4096).flatMap { keySize =>
      Blocker[F]
        .flatMap(BouncyCastleResource[F](_, removeOnClose = false))
        .evalMap { _ =>
          for {
            pair <- Sync[F].delay {
              val generator = KeyPairGenerator.getInstance("RSA", "BC")
              generator.initialize(keySize)
              generator.generateKeyPair()
            }
            now <- Clock[F].realTime(MILLISECONDS).map(new Date(_))
            pgpPair <- Sync[F].delay {
              new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, pair, now)
            }
          } yield pgpPair
        }
    }
  }

  implicit def arbPgpPublicKey[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPPublicKey]] = Arbitrary {
    arbKeyPair[F].arbitrary.map(_.map(_.getPublicKey))
  }

  implicit def arbPgpPrivateKey[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPPrivateKey]] = Arbitrary {
    arbKeyPair[F].arbitrary.map(_.map(_.getPrivateKey))
  }
}

object PgpArbitraries extends PgpArbitraries
