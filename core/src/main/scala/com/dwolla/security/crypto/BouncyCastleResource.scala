package com.dwolla.security.crypto

import java.security.Security
import cats.effect._
import cats.syntax.all._
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.util.concurrent.atomic.AtomicInteger

sealed trait BouncyCastleResource
object BouncyCastleResource {
  private val timesBouncyCastleHasBeenRegistered: AtomicInteger = new AtomicInteger(0)

  private def register(provider: BouncyCastleProvider): Unit =
    synchronized {
      val previousCount = timesBouncyCastleHasBeenRegistered.getAndIncrement()
      val position = Security.addProvider(provider)

      /**
       * If BouncyCastle was already registered (as indicated by the returned
       * position being -1) but we didn't register it (as indicated
       * by the previous count being 0, then increment the counter so we
       * don't end up removing it when all these BouncyCastleResources
       * go out of scope.
       */
      if (position == -1 && previousCount == 0)
        timesBouncyCastleHasBeenRegistered.incrementAndGet()

      ()
    }

  private def deregister(name: String): Unit =
    synchronized {
      val remaining = timesBouncyCastleHasBeenRegistered.decrementAndGet()

      if (0 == remaining)
        Security.removeProvider(name)
    }

  def apply[F[_] : Sync : ContextShift](blocker: Blocker): Resource[F, BouncyCastleResource] = {
    def registerBouncyCastle: F[String] =
      for {
        provider <- blocker.delay(new BouncyCastleProvider)
        _ <- blocker.delay(register(provider))
      } yield provider.getName

    def removeBouncyCastle(name: String): F[Unit] =
      blocker.delay(deregister(name))

    Resource.make(registerBouncyCastle)(removeBouncyCastle).as(new BouncyCastleResource {})
  }
}
