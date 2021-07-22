package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest.CatsResourceIO
import cats.syntax.all._
import com.dwolla.testutils._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalatest.flatspec._
import org.typelevel.log4cats.Logger
import org.scalacheck.cats.implicits._

import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._

class CryptoAlgSpec1
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[CryptoAlg[IO]]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, CryptoAlg[IO]] = Blocker[IO].flatMap(CryptoAlg[IO](_, removeOnClose = false))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "round trip the plaintext" in { crypto =>
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
}

class CryptoAlgSpec2
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[CryptoAlg[IO]]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, CryptoAlg[IO]] = Blocker[IO].flatMap(CryptoAlg[IO](_, removeOnClose = false))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "maintain chunk size throughout pipeline" in { crypto =>
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
}

class CryptoAlgSpec3
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[CryptoAlg[IO]]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, CryptoAlg[IO]] = Blocker[IO].flatMap(CryptoAlg[IO](_, removeOnClose = false))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "support armoring a PGP value" in { crypto =>
    forAll(arbPgpBytes[IO].arbitrary, MinSuccessful(1)) { (bytesR: Resource[IO, Array[Byte]]) =>
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
      } yield {
        armored should be(expected)
      }
    }
  }
}

class CryptoAlgSpec4
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[CryptoAlg[IO]]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, CryptoAlg[IO]] = Blocker[IO].flatMap(CryptoAlg[IO](_, removeOnClose = false))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  def genPGPSecretKeyRingCollection[F[_] : Sync : ContextShift : Clock](passphrase: Array[Char]): Gen[Resource[F, PGPSecretKeyRingCollection]] =
    (arbitrary[Resource[F, PGPKeyPair]], arbitrary[String]).mapN { (keyPairR, keyRingId) =>
      for {
        kp <- keyPairR
        generator <- Resource.eval(pgpKeyRingGenerator[F](keyRingId, kp, passphrase))
      } yield new PGPSecretKeyRingCollection(List(generator.generateSecretKeyRing()).asJava)
    }

  private def keysIn[F[_] : Sync](collection: PGPSecretKeyRingCollection): Stream[F, PGPSecretKey] =
    for {
      ring <- Stream.fromIterator[F](collection.iterator().asScala)
      key <- Stream.fromIterator[F](ring.iterator().asScala)
    } yield key

  it should "round trip the plaintext using a key ring collection" in { crypto =>
    forAll(MinSuccessful(1)) { (passphrase: Array[Char],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize) =>
      forAll(genPGPSecretKeyRingCollection[IO](passphrase), MinSuccessful(1)) { collectionR =>
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
}

class CryptoAlgSpec5
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[CryptoAlg[IO]]
    with CryptoArbitraries
    with Fs2PgpSpec {

  override def resource: Resource[IO, CryptoAlg[IO]] = Blocker[IO].flatMap(CryptoAlg[IO](_, removeOnClose = false))

  private implicit val noOpLogger: Logger[IO] = NoOpLogger[IO]()

  behavior of "CryptoAlg"

  it should "round trip the plaintext using a key ring" in { crypto =>
    forAll(MinSuccessful(1)) { (keyPairR: Resource[IO, PGPKeyPair],
                                bytesG: Stream[Pure, Byte],
                                encryptionChunkSize: ChunkSize,
                                decryptionChunkSize: ChunkSize,
                                keyRingId: String,
                                passphrase: Array[Char]) =>
      val materializedBytes: List[Byte] = bytesG.compile.toList

      for {
        kp <- keyPairR
        ring <- Resource.eval(pgpKeyRingGenerator[IO](keyRingId, kp, passphrase)).map(_.generateSecretKeyRing())
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

trait CryptoArbitraries { self: PgpArbitraries =>
  implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = arbWeakKeyPair[F]

  def genNBytesBetween(min: Int, max: Int): Gen[Stream[Pure, Byte]] =
    for {
      count <- Gen.chooseNum(min, Math.max(min, max))
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)

  implicit val arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    genNBytesBetween(1 << 10, 1 << 20) // 1KB to 1MB
  }

  def arbPgpBytes[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, Array[Byte]]] = Arbitrary {
    for {
      keyPair <- arbitrary[Resource[F, PGPKeyPair]]
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes
  }

  implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    chooseRefinedNum[Refined, Int, Positive](1024, 4096).map(tagChunkSize)
  }

  def pgpKeyRingGenerator[F[_] : Sync](keyRingId: String,
                                       keyPair: PGPKeyPair,
                                       passphrase: Array[Char]): F[PGPKeyRingGenerator] =
    Sync[F].delay {
      val pgpContentSignerBuilder = new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA1)
      val dc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
      val keyEncryptor = new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5).build(passphrase)

      new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION,
        keyPair,
        keyRingId,
        dc,
        null,
        null,
        pgpContentSignerBuilder,
        keyEncryptor
      )
    }
}
