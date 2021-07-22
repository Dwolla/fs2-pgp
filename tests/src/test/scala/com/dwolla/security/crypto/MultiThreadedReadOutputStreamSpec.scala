package com.dwolla.security.crypto

import cats.effect._
import com.dwolla.testutils.NoOpLogger
import fs2._
import org.bouncycastle.openpgp.PGPKeyPair
import org.scalatest.flatspec.AsyncFlatSpec
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor}

class MultiThreadedReadOutputStreamSpec
  extends AsyncFlatSpec
    with CryptoArbitraries
    with Fs2PgpSpec {

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "round trip the plaintext with a pathological ThreadPool" in {
    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytes: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      for {
        blocker <- Blocker.fromExecutorService[IO](IO {
          new ThreadPoolExecutor(0, Int.MaxValue, 0L, SECONDS, new SynchronousQueue[Runnable])
        })
        crypto <- CryptoAlg[IO](blocker)
        keyPair <- keyPairR
        roundTrip <- bytes
          .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
          .through(crypto.decrypt(keyPair.getPrivateKey, decryptionChunkSize))
          .compile
          .resource
          .toList
      } yield {
        roundTrip should be(bytes.toList)
      }
    }
  }
}
