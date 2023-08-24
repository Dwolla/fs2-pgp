package com.dwolla.testutils

import cats.effect.*
import cats.syntax.all.*
import com.dwolla.security.crypto.*
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all.*
import fs2.*
import org.bouncycastle.bcpg.*
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.*
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary.*
import org.scalacheck.*
import org.scalacheck.cats.implicits.*

import scala.jdk.CollectionConverters.*

trait CryptoArbitraries
  extends CryptoArbitrariesPlatform { self: PgpArbitraries =>
  def genNBytesBetween(min: Int, max: Int): Gen[Stream[Pure, Byte]] =
    for {
      count <- Gen.chooseNum(min, Math.max(min, max))
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)

  implicit val arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    genNBytesBetween(1 << 10, 1 << 20) // 1KB to 1MB
  }

  implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    chooseRefinedNum[Refined, Int, Positive](PosInt1024, PosInt4096).map(ChunkSize(_))
  }

  def genPgpBytes[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, Array[Byte]]] =
    for {
      keyPair <- arbitrary[Resource[F, PGPKeyPair]]
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes

  def pgpKeyRingGenerator[F[_] : Sync](keyRingId: String,
                                                keyPair: PGPKeyPair,
                                                passphrase: Array[Char]): F[PGPKeyRingGenerator] =
    Sync[F].blocking {
      val pgpContentSignerBuilder: PGPContentSignerBuilder = new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA1)
      //      val pgpContentSignerBuilder: PGPContentSignerBuilder = new BcPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA1)
      val dc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
      //      val dc: PGPDigestCalculator = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
      val keyEncryptor: PBESecretKeyEncryptor = new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5).build(passphrase)
      //      val keyEncryptor: PBESecretKeyEncryptor = new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5).build(passphrase)

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

  def genPGPSecretKeyRingCollection[F[_] : Sync](passphrase: Array[Char])
                                                         (implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, PGPSecretKeyRingCollection]] =
    (arbitrary[Resource[F, PGPKeyPair]], arbitrary[String]).mapN { (keyPairR, keyRingId) =>
      for {
        kp <- keyPairR
        generator <- Resource.eval(pgpKeyRingGenerator[F](keyRingId, kp, passphrase))
        collection <- Resource.eval(Sync[F].blocking(generator.generateSecretKeyRing()))
      } yield new PGPSecretKeyRingCollection(List(collection).asJava)
    }

  def keysIn[F[_] : Sync](collection: PGPSecretKeyRingCollection): Stream[F, PGPSecretKey] =
    for {
      ring <- Stream.fromBlockingIterator[F](collection.iterator().asScala, 1)
      key <- Stream.fromBlockingIterator[F](ring.iterator().asScala, 1)
    } yield key
}

object CryptoArbitraries extends CryptoArbitraries with PgpArbitraries
