package com.dwolla.security.crypto

import java.security.Security

import cats.effect._
import cats.syntax.all._
import org.bouncycastle.jce.provider.BouncyCastleProvider

sealed trait BouncyCastleResource
object BouncyCastleResource {
  def apply[F[_] : Sync : ContextShift](blocker: Blocker,
                                        removeOnClose: Boolean = true): Resource[F, BouncyCastleResource] = {
    def registerBouncyCastle: F[String] =
      for {
        provider <- blocker.delay(new BouncyCastleProvider)
        _ <- blocker.delay(Security.addProvider(provider))
      } yield provider.getName

    def removeBouncyCastle(name: String): F[Unit] =
      if (removeOnClose)
        blocker.delay(Security.removeProvider(name))
      else ().pure[F]

    Resource.make(registerBouncyCastle)(removeBouncyCastle).as(new BouncyCastleResource {})
  }
}
