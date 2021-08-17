package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import com.dwolla.testutils._
import fs2._
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalacheck.effect.PropF.{forAllF, forAllNoShrinkF}
import org.scalacheck.util.Pretty
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import com.eed3si9n.expecty.Expecty.{ assert => Assert }

import java.io.ByteArrayOutputStream
import scala.concurrent.duration._

class CryptoAlgSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PgpArbitraries
    with CryptoArbitraries {

  private val resource: Fixture[(Blocker, CryptoAlg[IO])] = ResourceSuiteLocalFixture("Blocker[IO] and CryptoAlg[IO]", Blocker[IO].mproduct(CryptoAlg[IO](_)))
  override def munitFixtures = List(resource)

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]

  override protected def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default
      .withMinSuccessfulTests(1)

  override val munitTimeout: Duration = 2.minutes

  test("CryptoAlg should round trip the plaintext using a key pair") {
    val (blocker, crypto) = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO](blocker)

    forAllF { (keyPairR: Resource[IO, PGPKeyPair],
               bytesG: Stream[Pure, Byte],
               encryptionChunkSize: ChunkSize,
               decryptionChunkSize: ChunkSize) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList
      val bytes = Stream.emits(materializedBytes)
      val testResource =
        for {
          keyPair <- keyPairR
          roundTrip <- bytes
            .through(crypto.encrypt(keyPair.getPublicKey, encryptionChunkSize))
            .through(crypto.armor(encryptionChunkSize))
            .through(crypto.decrypt(keyPair.getPrivateKey, decryptionChunkSize))
            .compile
            .resource
            .toList
        } yield roundTrip

      testResource.use { roundTrip => IO {
        assertEquals(roundTrip, materializedBytes)
      }}
    }
  }

  test("CryptoAlg should maintain chunk size throughout pipeline") {
    val (blocker, crypto) = resource()

    case class Inputs(keyPairR: Resource[IO, PGPKeyPair],
                      encryptionChunkSize: ChunkSize,
                      bytes: Stream[Pure, Byte],
                     )
    val genChunkSizeTestInputs: Gen[Inputs] =
      for {
        keyPairR <- genWeakKeyPair[IO](blocker)
        encryptionChunkSize <- arbitrary[ChunkSize]
        // since the cryptotext is compressed, we need to generate at least 10x the chunk size to
        // be fairly confident that there will be at least one full-sized chunk
        bytes <- genNBytesBetween(encryptionChunkSize.value * 10, 1 << 16)
      } yield Inputs(keyPairR, encryptionChunkSize, bytes)

    forAllNoShrinkF(genChunkSizeTestInputs) { case Inputs(keyPairR, encryptionChunkSize, bytes) =>
      val testResource =
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
        } yield chunkSizes

      testResource.use(chunkSizes => IO {
        Assert(chunkSizes.contains(encryptionChunkSize.value))
        Assert(Set(1, 2) contains chunkSizes.size)
      })
    }
  }

  test("CryptoAlg should support armoring a PGP value") {
    val (blocker, crypto) = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO](blocker)

    forAllF(genPgpBytes[IO]) { (bytesR: Resource[IO, Array[Byte]]) =>
      val testResource =
        for {
          blocker <- Blocker[IO]
          bytes <- bytesR
          armored <- Stream.emits(bytes).through(crypto.armor()).through(text.utf8Decode).compile.resource.string
          expected <- Resource.eval {
            for {
              out <- IO(new ByteArrayOutputStream())
              _ <- Resource.fromAutoCloseableBlocking(blocker)(IO(new ArmoredOutputStream(out))).evalMap(arm => IO(arm.write(bytes))).use(_ => IO.unit)
              s <- IO(new String(out.toByteArray))
            } yield s
          }
        } yield (armored, expected)

      testResource.use { case (armored, expected) => IO {
        assertEquals(armored, expected)
      }}
    }
  }

  test("CryptoAlg should round trip the plaintext using a key ring collection") {
    val (blocker, crypto) = resource()

    case class Inputs(passphrase: Array[Char],
                      bytesG: Stream[Pure, Byte],
                      encryptionChunkSize: ChunkSize,
                      decryptionChunkSize: ChunkSize,
                      collectionR: Resource[IO, PGPSecretKeyRingCollection]
                     )
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO](blocker)
    val genInputs: Gen[Inputs] =
      for {
        passphrase <- arbitrary[Array[Char]]
        bytesG <- arbitrary[Stream[Pure, Byte]]
        encryptionChunkSize <- arbitrary[ChunkSize]
        decryptionChunkSize <- arbitrary[ChunkSize]
        collectionR <- genPGPSecretKeyRingCollection[IO](blocker, passphrase)
      } yield Inputs(passphrase, bytesG, encryptionChunkSize, decryptionChunkSize, collectionR)

    forAllNoShrinkF(genInputs) { case Inputs(passphrase, bytesG, encryptionChunkSize, decryptionChunkSize, collectionR) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList

      val testResource =
        for {
          collection <- collectionR
          pub <- keysIn[IO](collection).map(_.getPublicKey).find(_.isEncryptionKey).compile.resource.lastOrError
          roundTrip <- Stream.emits(materializedBytes)
            .through(crypto.encrypt(pub, encryptionChunkSize))
            .through(crypto.armor(encryptionChunkSize))
            .through(crypto.decrypt(collection, passphrase, decryptionChunkSize))
            .compile
            .resource
            .toList
        } yield roundTrip

      testResource.use(roundTrip => IO {
        assertEquals(roundTrip, materializedBytes)
      })
    }
  }

  test("CryptoAlg should round trip the plaintext using a key ring") {
    val (blocker, crypto) = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO](blocker)

    forAllNoShrinkF { (keyPairR: Resource[IO, PGPKeyPair],
                       bytesG: Stream[Pure, Byte],
                       encryptionChunkSize: ChunkSize,
                       decryptionChunkSize: ChunkSize,
                       keyRingId: String,
                       passphrase: Array[Char]) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList

      val testResource =
        for {
          kp <- keyPairR
          ring <- Resource.eval(pgpKeyRingGenerator[IO](blocker)(keyRingId, kp, passphrase)).map(_.generateSecretKeyRing())
          roundTrip <- Stream.emits(materializedBytes)
            .through(crypto.encrypt(kp.getPublicKey, encryptionChunkSize))
            .through(crypto.armor(encryptionChunkSize))
            .through(crypto.decrypt(ring, passphrase, decryptionChunkSize))
            .compile
            .resource
            .toList
        } yield roundTrip

      testResource.use(roundTrip => IO {
        assertEquals(roundTrip, materializedBytes)
      })
    }
  }

  private implicit def prettyArrayChar: Array[Char] => Pretty = arr => Pretty { _ =>
    arr.toList.map("'" + _ + "'").mkString("Array(", ", ", ")")
  }
}
