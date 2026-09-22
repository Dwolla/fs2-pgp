package com.dwolla.security.crypto

import java.security.{NoSuchProviderException, Security}
import cats.effect._
import cats.syntax.all._
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

sealed trait BouncyCastleResource
object BouncyCastleResource {
  private val timesBouncyCastleHasBeenRegistered: AtomicInteger = new AtomicInteger(0)

  /**
   * A bounded, always-on log of register/deregister events, kept so that the rare
   * "no such provider: BC" failures this codebase has intermittently hit in CI over
   * the years can be diagnosed from the history that led up to them, instead of just
   * the bare exception.
   */
  private[crypto] val maxDebugHistoryEntries: Int = 200
  private val debugLog: ConcurrentLinkedDeque[String] = new ConcurrentLinkedDeque()

  private def logEvent(event: String): Unit = {
    debugLog.addLast(s"[${Instant.now()}] [${Thread.currentThread().getName}] $event")
    while (debugLog.size() > maxDebugHistoryEntries)
      debugLog.pollFirst()
  }

  private[crypto] def debugHistory: List[String] =
    debugLog.iterator().asScala.toList

  private def register(provider: BouncyCastleProvider): Unit =
    synchronized {
      val previousCount = timesBouncyCastleHasBeenRegistered.getAndIncrement()
      val position = Security.addProvider(provider)

      /*
       * If BouncyCastle was already registered (as indicated by the returned
       * position being -1) but we didn't register it (as indicated
       * by the previous count being 0, then increment the counter so we
       * don't end up removing it when all these BouncyCastleResources
       * go out of scope.
       */
      val corrected = position == -1 && previousCount == 0
      if (corrected)
        timesBouncyCastleHasBeenRegistered.incrementAndGet()

      logEvent(s"register: previousCount=$previousCount, addProviderPosition=$position, corrected=$corrected, count=${timesBouncyCastleHasBeenRegistered.get()}")
    }

  private def deregister(name: String): Unit =
    synchronized {
      val remaining = timesBouncyCastleHasBeenRegistered.decrementAndGet()
      val removedProvider = 0 == remaining

      if (removedProvider)
        Security.removeProvider(name)

      logEvent(s"deregister: remaining=$remaining, removedProvider=$removedProvider")
    }

  def apply[F[_] : Sync]: Resource[F, BouncyCastleResource] = {
    def registerBouncyCastle: F[String] =
      for {
        provider <- Sync[F].blocking(new BouncyCastleProvider)
        _ <- Sync[F].blocking(register(provider))
      } yield provider.getName

    def removeBouncyCastle(name: String): F[Unit] =
      Sync[F].blocking(deregister(name))

    Resource.make(registerBouncyCastle)(removeBouncyCastle).as(new BouncyCastleResource {})
  }

  /**
   * Wraps an effect that depends on the "BC" security provider being registered.
   * If it fails with a [[java.security.NoSuchProviderException]], the recent
   * register/deregister history is printed to stderr before the exception is
   * rethrown unchanged, so a rare occurrence of this failure in CI is
   * self-diagnosing instead of just a bare exception with no context.
   */
  def logHistoryIfProviderMissing[F[_], A](fa: F[A])(implicit F: Sync[F]): F[A] =
    fa.onError {
      case _: NoSuchProviderException =>
        F.blocking {
          val history = debugHistory
          Console.err.println(s"BouncyCastleResource: NoSuchProviderException encountered; recent registration history (${history.size} entries):")
          history.foreach(Console.err.println)
        }
    }
}
