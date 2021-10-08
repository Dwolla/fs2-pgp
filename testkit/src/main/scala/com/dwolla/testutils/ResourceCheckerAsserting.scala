package com.dwolla.testutils

import cats.effect._
import cats.syntax.all._
import org.scalactic.source
import org.scalatest.exceptions._
import org.scalatestplus.scalacheck._

import scala.concurrent.duration._
import cats.effect.Temporal

class ResourceCheckerAsserting[F[_] : ConcurrentEffect : Temporal, A](timeout: FiniteDuration = 1.minute)
  extends CheckerAsserting.CheckerAssertingImpl[Resource[F, A]] {

  private val teca = new TimeoutEffectCheckerAsserting[F, A](timeout)

  override type Result = F[Unit]

  override def succeed(result: Resource[F, A]): (Boolean, Option[Throwable]) =
    teca.succeed(result.use(x => x.pure[F]))

  override def indicateSuccess(message: => String): Result =
    teca.indicateSuccess(message)

  override def indicateFailure(messageFun: StackDepthException => String,
                               undecoratedMessage: => String,
                               scalaCheckArgs: List[Any],
                               scalaCheckLabels: List[String],
                               optionalCause: Option[Throwable],
                               pos: source.Position
                              ): Result =
    teca.indicateFailure(messageFun, undecoratedMessage, scalaCheckArgs, scalaCheckLabels, optionalCause, pos)
}
