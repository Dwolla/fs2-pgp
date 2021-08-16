package com.dwolla.security.crypto

import cats.effect._
import com.dwolla.testutils._
import fs2._
import munit._
import org.bouncycastle.openpgp.PGPKeyPair
import org.scalacheck.{Arbitrary, Test}
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor}

class MultiThreadedReadOutputStreamSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PgpArbitraries
    with CryptoArbitraries {

  private val resource = ResourceSuiteLocalFixture("Blocker[IO]", Blocker.fromExecutorService[IO](IO {
    new ThreadPoolExecutor(0, Int.MaxValue, 0L, SECONDS, new SynchronousQueue[Runnable])
  }))
  override def munitFixtures = List(resource)

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]

  override protected def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default
      .withMinSuccessfulTests(1)

  test("CryptoAlg should round trip the plaintext with a pathological ThreadPool") {
    val blocker = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAllF { (keyPairR: Resource[IO, PGPKeyPair],
               bytes: Stream[Pure, Byte],
               encryptionChunkSize: ChunkSize,
               decryptionChunkSize: ChunkSize) =>
      val testResource = for {
        crypto <- CryptoAlg[IO](blocker)
        keyPair <- keyPairR
        roundTrip <- bytes
          .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
          .through(crypto.decrypt(keyPair.getPrivateKey, decryptionChunkSize))
          .compile
          .resource
          .toList
      } yield (roundTrip, bytes.toList)

      testResource.use { case (roundTrip, bytes) => IO {
        assertEquals(roundTrip, bytes)
      }}
    }
  }
}
