package com.dwolla.security.crypto

import java.security.Security
import cats.effect.*
import cats.syntax.all.*
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.nowarn

sealed trait BouncyCastleResource
object BouncyCastleResource {
  private val timesBouncyCastleHasBeenRegistered: AtomicInteger =
    new AtomicInteger(0)

  private def register(provider: BouncyCastleProvider): Unit =
    synchronized {
      @nowarn("msg=discarded non-Unit value of type Int")
      def increment(): Unit =
        timesBouncyCastleHasBeenRegistered.incrementAndGet()

      val previousCount = timesBouncyCastleHasBeenRegistered.getAndIncrement()
      val position      = Security.addProvider(provider)

      /*
       * If BouncyCastle was already registered (as indicated by the returned
       * position being -1) but we didn't register it (as indicated
       * by the previous count being 0, then increment the counter so we
       * don't end up removing it when all these BouncyCastleResources
       * go out of scope.
       */
      if (position == -1 && previousCount == 0)
        increment()

      ()
    }

  private def deregister(name: String): Unit =
    synchronized {
      val remaining = timesBouncyCastleHasBeenRegistered.decrementAndGet()

      if (0 == remaining)
        Security.removeProvider(name)
    }

  def apply[F[_]: Sync]: Resource[F, BouncyCastleResource] = {
    def registerBouncyCastle: F[String] =
      for {
        provider <- Sync[F].blocking(new BouncyCastleProvider)
        _        <- Sync[F].blocking(register(provider))
      } yield provider.getName

    def removeBouncyCastle(name: String): F[Unit] =
      Sync[F].blocking(deregister(name))

    Resource
      .make(registerBouncyCastle)(removeBouncyCastle)
      .as(new BouncyCastleResource {})
  }
}
