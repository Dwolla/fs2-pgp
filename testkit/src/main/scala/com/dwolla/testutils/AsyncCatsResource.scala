package com.dwolla.testutils

import cats.effect._
import cats.effect.syntax.all._
import org.scalatest._

import scala.concurrent.duration._

trait AsyncCatsResource[F[_], A] extends BeforeAndAfterAll { self: FixtureAsyncTestSuite =>
  def resource: Resource[F, A]

  implicit def ResourceEffect: Effect[F]
  protected val ResourceTimeout: Duration = 10.seconds

  private var value: Option[A] = None
  private var shutdown: F[Unit] = ResourceEffect.unit

  override def beforeAll(): Unit = {
    ResourceEffect
      .map(resource.allocated) {
        case (a, shutdownAction) =>
          value = Some(a)
          shutdown = shutdownAction
      }
      .toIO
      .unsafeRunTimed(ResourceTimeout)

    ()
  }

  override def afterAll(): Unit = {
    shutdown.toIO.unsafeRunTimed(ResourceTimeout)
    value = None
    shutdown = ResourceEffect.unit
  }

  override type FixtureParam = A

  override def withFixture(test: OneArgAsyncTest): FutureOutcome =
    withFixture(test.toNoArgAsyncTest(value.getOrElse {
      fail("Resource Not Initialized When Trying to Use")
    }))

}

trait AsyncCatsResourceIO[A] extends AsyncCatsResource[IO, A] { self: FixtureAsyncTestSuite =>
  override implicit def ResourceEffect: Effect[IO] = IO.ioEffect
}
