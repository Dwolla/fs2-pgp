package com.dwolla.security.crypto

import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import com.dwolla.testutils.*
import com.eed3si9n.expecty.Expecty.assert as Assert
import fs2.*
import munit.catseffect.IOFixture
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.openpgp.*
import org.scalacheck.Arbitrary.*
import org.scalacheck.*
import org.scalacheck.effect.PropF.{forAllF, forAllNoShrinkF}
import org.scalacheck.util.Pretty
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

class CryptoAlgSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PgpArbitraries
    with CryptoArbitraries {

  private implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val resource = ResourceSuiteLocalFixture("CryptoAlg[IO]", CryptoAlg.resource[IO])
  override def munitFixtures: Seq[IOFixture[?]] = List(resource)

  override protected def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default
      .withMinSuccessfulTests(10)

  override val munitIOTimeout: Duration = 2.minutes

  private def genNelResource[F[_], A](implicit A: Arbitrary[Resource[F, A]]): Gen[Resource[F, NonEmptyList[A]]] =
    for {
      extraCount <- Gen.chooseNum(0, 10)
      a <- A.arbitrary
      extras <- Gen.listOfN(extraCount, A.arbitrary)
    } yield {
      for {
        aa <- a
        ee <- extras.sequence
      } yield NonEmptyList.of(aa, ee: _*)
    }

  private implicit def arbNelResource[F[_], A](implicit A: Arbitrary[Resource[F, A]]): Arbitrary[Resource[F, NonEmptyList[A]]] = Arbitrary(genNelResource[F, A])

  test("CryptoAlg should round trip the plaintext using one or more key pairs") {
    val crypto = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO]

    forAllF { (keyPairsR: Resource[IO, NonEmptyList[PGPKeyPair]],
               bytesG: Stream[Pure, Byte],
               encryptionChunkSize: ChunkSize,
               decryptionChunkSize: ChunkSize) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList
      val bytes = Stream.emits(materializedBytes)
      val testResource =
        LoggerFactory[IO]
          .create(LoggerName("CryptoAlgSpec.round trip"))
          .toResource
          .evalTap(_.trace("starting"))
          .flatMap { implicit l =>
            for {
              keyPairs <- keyPairsR
              _ <- Logger[IO].trace("key pairs generated").toResource
              allRecipients = keyPairs.map(_.getPublicKey)
              privateKeys = keyPairs.map(_.getPrivateKey)
              _ <- Logger[IO].trace(s"encrypting with keys ${allRecipients.map(_.getKeyID)}").toResource
              encryptedBytes <-
                bytes
                  .through(crypto.encrypt(allRecipients, EncryptionConfig().withChunkSize(encryptionChunkSize)))
                  .through(crypto.armor(encryptionChunkSize))
                  .compile
                  .resource
                  .toVector

              decryptedBytes <-
                privateKeys.traverse { privateKey =>
                  Logger[IO].trace(s"decrypting with key id ${privateKey.getKeyID}").toResource >>
                    Stream.emits(encryptedBytes)
                    .through(crypto.decrypt(privateKey, decryptionChunkSize))
                    .compile
                    .resource
                    .toList
                }
              _ <- Logger[IO].trace("done with round trips").toResource
            } yield decryptedBytes
          }

      testResource.use {
        _.traverse_ { roundTrippedBytes => IO {
          assertEquals(roundTrippedBytes, materializedBytes)
        }}
      }
    }
  }

  test("CryptoAlg should maintain chunk size throughout pipeline") {
    val crypto = resource()

    case class Inputs(keyPairR: Resource[IO, PGPKeyPair],
                      encryptionChunkSize: ChunkSize,
                      bytes: Stream[Pure, Byte],
                     )
    val genChunkSizeTestInputs: Gen[Inputs] =
      for {
        keyPairR <- genWeakKeyPair[IO]
        encryptionChunkSize <- arbitrary[ChunkSize]
        // since the cryptotext is compressed, we need to generate at least 10x the chunk size to
        // be fairly confident that there will be at least one full-sized chunk
        bytes <- genNBytesBetween(encryptionChunkSize.unrefined * 10, 1 << 16)
      } yield Inputs(keyPairR, encryptionChunkSize, bytes)

    forAllNoShrinkF(genChunkSizeTestInputs) { case Inputs(keyPairR, encryptionChunkSize, bytes) =>
      val testResource =
        for {
          keyPair <- keyPairR
          chunkSizes <- bytes
            .through(crypto.encrypt(EncryptionConfig().withChunkSize(encryptionChunkSize), keyPair.getPublicKey))
            .through(crypto.armor(encryptionChunkSize))
            .chunks
            .map(_.size)
            .compile
            .resource
            .to(Set)
        } yield chunkSizes

      testResource.use(chunkSizes => IO {
        Assert(chunkSizes.contains(encryptionChunkSize.unrefined))
        Assert(Set(1, 2) contains chunkSizes.size)
      })
    }
  }

  test("CryptoAlg should round trip the plaintext using a key ring collection") {
    val crypto = resource()

    case class Inputs(passphrase: Array[Char],
                      bytesG: Stream[Pure, Byte],
                      encryptionChunkSize: ChunkSize,
                      decryptionChunkSize: ChunkSize,
                      collectionR: Resource[IO, PGPSecretKeyRingCollection]
                     )
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO]
    val genInputs: Gen[Inputs] =
      for {
        passphrase <- arbitrary[Array[Char]]
        bytesG <- arbitrary[Stream[Pure, Byte]]
        encryptionChunkSize <- arbitrary[ChunkSize]
        decryptionChunkSize <- arbitrary[ChunkSize]
        collectionR <- genPGPSecretKeyRingCollection[IO](passphrase)
      } yield Inputs(passphrase, bytesG, encryptionChunkSize, decryptionChunkSize, collectionR)

    forAllNoShrinkF(genInputs) { case Inputs(passphrase, bytesG, encryptionChunkSize, decryptionChunkSize, collectionR) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList

      val testResource =
        for {
          collection <- collectionR
          pub <- keysIn[IO](collection).map(_.getPublicKey).find(_.isEncryptionKey).compile.resource.lastOrError
          roundTrip <- Stream.emits(materializedBytes)
            .through(crypto.encrypt(EncryptionConfig().withChunkSize(encryptionChunkSize), pub))
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
    val crypto = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO]

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
          ring <- Resource.eval(pgpKeyRingGenerator[IO](keyRingId, kp, passphrase)).map(_.generateSecretKeyRing())
          roundTrip <- Stream.emits(materializedBytes)
            .through(crypto.encrypt(EncryptionConfig().withChunkSize(encryptionChunkSize), kp.getPublicKey))
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
