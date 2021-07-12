package com.dwolla.testutils

import java.time.Instant

import cats.effect._
import cats.syntax.all._
import org.typelevel.log4cats._

import scala.concurrent.duration._

object NoOpLogger {
  private def printMsg[F[_] : Sync : Clock](message: => String): F[Unit] =
    Clock[F].realTime(MILLISECONDS).map(Instant.ofEpochMilli).flatMap { now =>
      Sync[F].delay(println(s"$now $message"))
    }

  def apply[F[_] : Sync : Clock](print: Boolean = false): Logger[F] = new Logger[F] {
    override def error(t: Throwable)(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def warn(t: Throwable)(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def info(t: Throwable)(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def debug(t: Throwable)(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def trace(t: Throwable)(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def error(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def warn(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def info(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def debug(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
    override def trace(message: => String): F[Unit] = if(print) printMsg[F](message) else ().pure[F]
  }
}
