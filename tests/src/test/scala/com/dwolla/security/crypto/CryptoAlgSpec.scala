package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest.CatsResourceIO
import com.dwolla.testutils._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import org.typelevel.log4cats.Logger
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalatest.flatspec._

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor}

class CryptoAlgSpec
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[Blocker]
    with Fs2PgpSpec {

  override def resource: Resource[IO, Blocker] = Blocker[IO]

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  private implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = arbWeakKeyPair[F]

  behavior of "CryptoAlg"

  private def genNBytesBetween(min: Int, max: Int): Gen[Stream[Pure, Byte]] =
    for {
      count <- Gen.chooseNum(min, Math.max(min, max))
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)

  private implicit val arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    genNBytesBetween(1 << 10, 1 << 20) // 1KB to 1MB
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

  it should "round trip the plaintext with a pathological ThreadPool" in { _ =>
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

  it should "round trip the plaintext" in { blocker =>
    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList
      val bytes = Stream.emits(materializedBytes)
      for {
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
        keyPair <- keyPairR
        roundTrip <- bytes
          .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
          .through(crypto.armor(encryptionChunkSize))
          .through(crypto.decrypt(keyPair.getPrivateKey, decryptionChunkSize))
          .compile
          .resource
          .toList
      } yield {
        roundTrip should be(materializedBytes)
      }
    }
  }

  it should "maintain chunk size throughout pipeline" in { blocker =>
    forAll { (keyPairR: Resource[IO, PGPKeyPair],
              encryptionChunkSize: ChunkSize) =>
      // since the cryptotext is compressed, we need to generate at least 10x the chunk size to
      // be fairly confident that there will be at least one full-sized chunk
      forAll(genNBytesBetween(encryptionChunkSize.value * 10, 1 << 16)) { (bytes: Stream[Pure, Byte]) =>
        for {
          crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
          keyPair <- keyPairR
          chunkSizes <- bytes
            .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
            .through(crypto.armor(encryptionChunkSize))
            .chunks
            .map(_.size)
            .compile
            .resource
            .to(Set)
        } yield {
          chunkSizes should contain(encryptionChunkSize.value)
          chunkSizes should (have size 2L or have size 1L)
        }
      }
    }
  }

  it should "support armoring a PGP value" in { blocker =>
    forAll(arbPgpBytes[IO].arbitrary) { (bytesR: Resource[IO, Array[Byte]]) =>
      for {
        crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
        bytes <- bytesR
        armored <- Stream.emits(bytes).through(crypto.armor()).through(text.utf8Decode).compile.resource.string
        expected <- Resource.eval {
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
