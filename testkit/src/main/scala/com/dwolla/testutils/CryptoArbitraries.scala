package com.dwolla.testutils

import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto._
import fs2._
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator._
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary._
import org.scalacheck._
import org.scalacheck.cats.implicits._

import scala.jdk.CollectionConverters._

trait CryptoArbitraries extends CryptoArbitrariesPlatform { self: PgpArbitraries =>
  def genNBytesBetween(min: Int, max: Int): Gen[Stream[Pure, Byte]] =
    for {
      count <- Gen.chooseNum(min, Math.max(min, max))
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)

  implicit val arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    genNBytesBetween(1 << 10, 1 << 20) // 1KB to 1MB
  }

  implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {4096 }

  override def genPgpBytes[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, Array[Byte]]] =
    for {
      keyPair <- arbitrary[Resource[F, PGPKeyPair]]
      bytes <- Gen.oneOf[Resource[F, Array[Byte]]](keyPair.map(_.getPublicKey.getEncoded), keyPair.map(_.getPrivateKey.getPrivateKeyDataPacket.getEncoded))
    } yield bytes

  override def pgpKeyRingGenerator[F[_] : Sync](keyRingId: String,
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

  override def genPGPSecretKeyRingCollection[F[_] : Sync](passphrase: Array[Char])
                                                         (implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, PGPSecretKeyRingCollection]] =
    (arbitrary[Resource[F, PGPKeyPair]], arbitrary[String]).mapN { (keyPairR, keyRingId) =>
      for {
        kp <- keyPairR
        generator <- Resource.eval(pgpKeyRingGenerator[F](keyRingId, kp, passphrase))
        collection <- Resource.eval(Sync[F].blocking(generator.generateSecretKeyRing()))
      } yield new PGPSecretKeyRingCollection(List(collection).asJava)
    }

  override def keysIn[F[_] : Sync](collection: PGPSecretKeyRingCollection): Stream[F, PGPSecretKey] =
    for {
      ring <- Stream.fromBlockingIterator[F](collection.iterator().asScala, 1)
      key <- Stream.fromBlockingIterator[F](ring.iterator().asScala, 1)
    } yield key
}

object CryptoArbitraries extends CryptoArbitraries with PgpArbitraries

// only kept to maintain binary compatibility
trait CryptoArbitrariesPlatform {
  private[testutils] def genPgpBytes[F[_]](implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, Array[Byte]]] =
    CryptoArbitraries.genPgpBytes

  private[testutils] def pgpKeyRingGenerator[F[_] : Sync](keyRingId: String,
                                                          keyPair: PGPKeyPair,
                                                          passphrase: Array[Char]): F[PGPKeyRingGenerator] =
    CryptoArbitraries.pgpKeyRingGenerator(keyRingId, keyPair, passphrase)

  private[testutils] def genPGPSecretKeyRingCollection[F[_] : Sync](passphrase: Array[Char])
                                                                   (implicit A: Arbitrary[Resource[F, PGPKeyPair]]): Gen[Resource[F, PGPSecretKeyRingCollection]] =
    CryptoArbitraries.genPGPSecretKeyRingCollection(passphrase)

  private[testutils] def keysIn[F[_] : Sync](collection: PGPSecretKeyRingCollection): Stream[F, PGPSecretKey] =
    CryptoArbitraries.keysIn(collection)
}
