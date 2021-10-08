package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest.CatsResourceIO
import cats.syntax.all._
import com.dwolla.testutils._
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalatest.flatspec._
import org.typelevel.log4cats.Logger

import java.io.ByteArrayOutputStream
import cats.effect.Resource

class CryptoAlgSpec
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[(Blocker, CryptoAlg[IO])]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, (Blocker, CryptoAlg[IO])] = Resource.unit[IO].mproduct(CryptoAlg[IO](_))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "round trip the plaintext" in { tuple =>
    val (blocker, crypto) = tuple
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList
      val bytes = Stream.emits(materializedBytes)
      for {
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

  it should "maintain chunk size throughout pipeline" in { tuple =>
    val (blocker, crypto) = tuple
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                encryptionChunkSize: ChunkSize) =>
      // since the cryptotext is compressed, we need to generate at least 10x the chunk size to
      // be fairly confident that there will be at least one full-sized chunk
      forAll(genNBytesBetween(encryptionChunkSize.value * 10, 1 << 16), MinSuccessful(1)) { (bytes: Stream[Pure, Byte]) =>
        for {
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

  it should "support armoring a PGP value" in { tuple =>
    val (blocker, crypto) = tuple
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAll(arbPgpBytes[IO].arbitrary, MinSuccessful(1)) { (bytesR: Resource[IO, Array[Byte]]) =>
      for {
        blocker <- Resource.unit[IO]
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

  it should "round trip the plaintext using a key ring collection" in { tuple =>
    val (blocker, crypto) = tuple
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAll(MinSuccessful(1)) { (passphrase: Array[Char],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      forAll(genPGPSecretKeyRingCollection[IO](blocker, passphrase), MinSuccessful(1)) { collectionR =>
        val materializedBytes: List[Byte] = bytesG.compile.toList

        for {
          collection <- collectionR
          pub <- keysIn(collection).map(_.getPublicKey).find(_.isEncryptionKey).compile.resource.lastOrError
          roundTrip <- Stream.emits(materializedBytes)
            .through(crypto.encrypt(pub, encryptionChunkSize))
            .through(crypto.armor(encryptionChunkSize))
            .through(crypto.decrypt(collection, passphrase, decryptionChunkSize))
            .compile
            .resource
            .toList
        } yield {
          roundTrip should be(materializedBytes)
        }
      }
    }
  }

  it should "round trip the plaintext using a key ring" in { tuple =>
    val (blocker, crypto) = tuple
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair(blocker)

    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize,
                                keyRingId: String,
                                passphrase: Array[Char]) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList

      for {
        kp <- keyPairR
        ring <- Resource.eval(pgpKeyRingGenerator[IO](blocker)(keyRingId, kp, passphrase)).map(_.generateSecretKeyRing())
        roundTrip <- Stream.emits(materializedBytes)
          .through(crypto.encrypt(kp.getPublicKey, encryptionChunkSize))
          .through(crypto.armor(encryptionChunkSize))
          .through(crypto.decrypt(ring, Array.empty[Char], decryptionChunkSize))
          .compile
          .resource
          .toList
      } yield {
        roundTrip should be(materializedBytes)
      }
    }
  }

}
