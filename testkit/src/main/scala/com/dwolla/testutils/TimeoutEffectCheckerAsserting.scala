package com.dwolla.testutils

import cats.effect._
import cats.effect.syntax.all._
import cats.effect.testing.scalatest.scalacheck.EffectCheckerAsserting

import scala.concurrent.duration._
import cats.effect.Temporal

class TimeoutEffectCheckerAsserting[F[_] : ConcurrentEffect : Temporal, A](timeout: FiniteDuration = 1.minute)
  extends EffectCheckerAsserting[F, A] {

  override def succeed(result: F[A]): (Boolean, Option[Throwable]) =
    super.succeed(result.timeout(timeout))

}
