package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import com.dwolla.testutils.{PgpArbitraries, ResourceCheckerAsserting, TimeoutEffectCheckerAsserting}
import org.scalatest.AsyncTestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{CheckerAsserting, ScalaCheckPropertyChecks}

trait Fs2PgpSpec
  extends AsyncIOSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with PgpArbitraries { asyncTestSuite: AsyncTestSuite =>
  protected implicit def ioCheckingAsserting[A]: CheckerAsserting[Resource[IO, A]] { type Result = IO[Unit] } =
    new ResourceCheckerAsserting[IO, A]

  protected implicit def timeoutEffectCheckerAsserting[F[_] : ConcurrentEffect : Timer, A]: CheckerAsserting[F[A]] { type Result = F[Unit] } =
    new TimeoutEffectCheckerAsserting[F, A]
}
