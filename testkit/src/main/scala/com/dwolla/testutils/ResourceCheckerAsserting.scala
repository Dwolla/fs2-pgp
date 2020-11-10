package com.dwolla.testutils

import cats.effect._
import cats.effect.testing.scalatest.scalacheck.EffectCheckerAsserting
import cats.syntax.all._
import cats.effect.syntax.all._
import org.scalactic.source
import org.scalatest.exceptions._
import org.scalatestplus.scalacheck._

import scala.concurrent.duration._

class ResourceCheckerAsserting[F[_] : ConcurrentEffect : Timer, A](timeout: FiniteDuration = 1.minute) extends CheckerAsserting.CheckerAssertingImpl[Resource[F, A]] {
  private val effectCheckerAsserting = new EffectCheckerAsserting[F, A]
  override type Result = F[Unit]

  override def succeed(result: Resource[F, A]): (Boolean, Option[Throwable]) =
    effectCheckerAsserting.succeed(result.use(x => x.pure[F]).timeout(timeout))

  override def indicateSuccess(message: => String): Result = ().pure[F]

  override def indicateFailure(messageFun: StackDepthException => String,
                               undecoratedMessage: => String,
                               scalaCheckArgs: List[Any],
                               scalaCheckLabels: List[String],
                               optionalCause: Option[Throwable],
                               pos: source.Position
                              ): Result =
    new GeneratorDrivenPropertyCheckFailedException(
      messageFun,
      optionalCause,
      pos,
      None,
      undecoratedMessage,
      scalaCheckArgs,
      None,
      scalaCheckLabels
    ).raiseError[F, Unit]

}
