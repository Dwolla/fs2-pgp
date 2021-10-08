package com.dwolla.testutils

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto._
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
import org.scalacheck.cats.implicits._

import scala.jdk.CollectionConverters._
import cats.effect.Sync

trait CryptoArbitraries { self: PgpArbitraries =>
  def genNBytesBetween(min: Int, max: Int): Gen[Stream[Pure, Byte]] =
    for {
      count <- Gen.chooseNum(min, Math.max(min, max))
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)

  implicit val arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    genNBytesBetween(1 << 10, 1 << 20) // 1KB to 1MB
  }

  def arbPgpBytes[F[_] : Applicative](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Arbitrary[Resource[F, Array[Byte]]] = Arbitrary {
    for {
      keyPair <- arbitrary[Resource[F, PGPKeyPair]]
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes
  }

  implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    chooseRefinedNum[Refined, Int, Positive](1024, 4096).map(tagChunkSize)
  }

  def pgpKeyRingGenerator[F[_] : Sync : ContextShift]
                                                     (keyRingId: String,
                                                      keyPair: PGPKeyPair,
                                                      passphrase: Array[Char]): F[PGPKeyRingGenerator] =
    Sync[F].blocking {
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

  def genPGPSecretKeyRingCollection[F[_] : Sync : ContextShift](passphrase: Array[Char])
                                                               (implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, PGPSecretKeyRingCollection]] =
    (arbitrary[Resource[F, PGPKeyPair]], arbitrary[String]).mapN { (keyPairR, keyRingId) =>
      for {
        kp <- keyPairR
        generator <- Resource.eval(pgpKeyRingGenerator[F](blocker)(keyRingId, kp, passphrase))
      } yield new PGPSecretKeyRingCollection(List(generator.generateSecretKeyRing()).asJava)
    }

  def keysIn[F[_] : Sync](collection: PGPSecretKeyRingCollection): Stream[F, PGPSecretKey] =
    for {
      ring <- Stream.fromIterator[F](collection.iterator().asScala)
      key <- Stream.fromIterator[F](ring.iterator().asScala)
    } yield key

}
