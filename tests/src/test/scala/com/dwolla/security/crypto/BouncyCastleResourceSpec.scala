package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import munit.CatsEffectSuite

import java.security.NoSuchProviderException

class BouncyCastleResourceSpec extends CatsEffectSuite {

  test("acquiring and releasing BouncyCastleResource records register and deregister events in the debug history") {
    for {
      _ <- BouncyCastleResource[IO].use(_ => IO.unit)
      history <- IO(BouncyCastleResource.debugHistory)
    } yield {
      assert(history.exists(_.contains("register")), s"expected a register event in $history")
      assert(history.exists(_.contains("deregister")), s"expected a deregister event in $history")
    }
  }

  test("the debug history stays bounded (not unbounded growth) even after many more acquire/release cycles than its capacity") {
    val cycles = BouncyCastleResource.maxDebugHistoryEntries * 3

    // the trim happens on every append rather than as a single atomic step across all concurrent
    // appenders, so under heavy contention from other suites sharing this same global buffer, the
    // size can transiently overshoot the cap by a small amount before self-correcting. The
    // invariant this test cares about is that it doesn't grow unbounded, not exact precision.
    val generousBound = BouncyCastleResource.maxDebugHistoryEntries * 2

    for {
      _ <- List.range(0, cycles).traverse_(_ => BouncyCastleResource[IO].use(_ => IO.unit))
      history <- IO(BouncyCastleResource.debugHistory)
    } yield assert(
      history.size <= generousBound,
      s"expected history bounded around ${BouncyCastleResource.maxDebugHistoryEntries} entries, but it had ${history.size}"
    )
  }

  test("logHistoryIfProviderMissing rethrows a NoSuchProviderException after logging the debug history") {
    val boom = new NoSuchProviderException("BC")

    BouncyCastleResource.logHistoryIfProviderMissing(IO.raiseError[Unit](boom))
      .attempt
      .assertEquals(Left(boom))
  }

  test("logHistoryIfProviderMissing does not intercept unrelated exceptions") {
    val boom = new RuntimeException("unrelated failure")

    BouncyCastleResource.logHistoryIfProviderMissing(IO.raiseError[Unit](boom))
      .attempt
      .assertEquals(Left(boom))
  }

  test("logHistoryIfProviderMissing passes successful results through untouched") {
    BouncyCastleResource.logHistoryIfProviderMissing(IO.pure(42))
      .assertEquals(42)
  }
}
