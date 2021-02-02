package com.dwolla.security.crypto

import cats.effect._
import com.dwolla.testutils._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import io.chrisdavenport.log4cats.Logger
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalatest.flatspec.AsyncFlatSpec

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor}

class CryptoAlgSpec
  extends AsyncFlatSpec
    with Fs2PgpSpec {
  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  private implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = arbWeakKeyPair[F]

  behavior of "CryptoAlg"

  private implicit def arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    for {
      count <- Gen.chooseNum(5000000, 20000000)
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)
  }

  private def arbPgpBytes[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, Array[Byte]]] = Arbitrary {
    for {
      keyPair <- arbitrary[Resource[F, PGPKeyPair]]
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes
  }

  private implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    chooseRefinedNum[Refined, Int, Positive](1024, 4096).map(tagChunkSize)
  }

  it should "round trip the plaintext with a pathological ThreadPool" in {
    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytes: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      for {
        blocker <- Blocker.fromExecutorService[IO](IO {
          new ThreadPoolExecutor(0, Int.MaxValue, 0L, SECONDS, new SynchronousQueue[Runnable])
        })
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
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

  it should "round trip the plaintext" in {
    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytes: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      for {
        blocker <- Blocker[IO]
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
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

  it should "support armoring a PGP value" in {
    forAll(arbPgpBytes[IO].arbitrary) { (bytesR: Resource[IO, Array[Byte]]) =>
      for {
        blocker <- Blocker[IO]
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
        bytes <- bytesR
        armored <- Stream.emits(bytes).through(crypto.armor()).through(text.utf8Decode).compile.resource.string
        expected <- Resource.liftF {
          for {
            out <- IO(new ByteArrayOutputStream())
            _ <- Resource.fromAutoCloseableBlocking(blocker)(IO(new ArmoredOutputStream(out))).evalMap(arm => IO(arm.write(bytes))).use(_ => IO.unit)
            s <- IO(new String(out.toByteArray))
          } yield s
        }
      } yield {
        armored should be(expected)
      }
    }
  }
}
