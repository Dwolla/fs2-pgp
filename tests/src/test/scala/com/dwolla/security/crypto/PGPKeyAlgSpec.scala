package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import com.dwolla.testutils._
import dev.holt.javatime.literals._
import fs2._
import fs2.io.readOutputStream
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.bcpg.{HashAlgorithmTags, PublicKeyAlgorithmTags, SymmetricKeyAlgorithmTags}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF
import com.eed3si9n.expecty.Expecty.{assert => Assert}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.jdk.CollectionConverters._

class PGPKeyAlgSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PgpArbitraries
    with CryptoArbitraries {

  private implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  test("PGPKeyAlg should load a PGPPublicKey from armored public key") {
    val key =
      """-----BEGIN PGP PUBLIC KEY BLOCK-----
        |Version: GnuPG v1
        |
        |mQENBFVoyeYBCACy0S/9y/c/CpoYLL6aD3TMCV1Pe/0jcWN0ykULf9l4znYODZLr
        |f10BGAJETj9ghrJCNXMib2ogz0wo43KVAp9o3mkg01vVyqs1rzM5jw+yCZmyGPFf
        |GsE2lxZFMX+rS0dyq2w0FQN2IjYsELwIFeQ02GXTLyTlhY+u5wwCXo4e7AEXaEo7
        |jl8129NA46gf6l+6lUMyFpKnunO7L4W5rCCrIizP4Fmll1adYfClSX6cztIfz4vg
        |Fs2HuViPin5y8THodkg9cIkCfyNHivEbfBbx0xfx67BCwxFcYgF/84H8TASRhjRl
        |4s1fZDA7rETWDJIcC+neNV/qtF0kY1ECSd3nABEBAAG0IFRlc3QgVXNlciA8ZnJl
        |ZCt0ZXN0QGR3b2xsYS5jb20+iQE4BBMBAgAiBQJVaMnmAhsDBgsJCAcDAgYVCAIJ
        |CgsEFgIDAQIeAQIXgAAKCRA2OYfNakCqV1bYB/9QNR5DN5J27Z4DIGoOto/PuVvs
        |bQHZj8NLcvIZL1cUyKOg+oRICq2z4BXHAMqyouhs/GLiR5P74I9cJTSIudAvBhwi
        |du9AcMQy+Qg3K1rUQGlNU+iamD8DFNUhLoK+Oicij0Mw4TSWBsoR3+Pg/jZ5SDUc
        |dUsGGaBJthYoiJR8vZ6Uf9oCn+mpVhrso0zemBDud4AHKaVa+8o7VUWGa6jeyRHX
        |RKVbHn7GGYiHZkl+qfpthcxyPHOIkZo+t8GVTItLpvVuU+X36N70+rIzXj5t8NDZ
        |KfD3M4p6BSq6Cu6DtJOZ1F28hwaWiRoCdbPrJfW33fo1RxLB6+nLf/ttYGmhuQEN
        |BFVoyeYBCADiZfKA98YQip/kvj5rBS2ilQDycBX7Ls2IftuwzO6Q9QSF2lDiz708
        |zyvg0czQPZYaZkFgziZEmjbvOc7hDG+icVWRLCjCcZk4i2TXy7bGcTZmBQ31iVMJ
        |ia7GxsJhu4ngrP15pZakAYcCwEk3QH17TdhOwvV8ixHmv9USCMJyiNnuhVAP2tY/
        |Ef0EoCV6qAMoP3dNPT30sFI8+55Ce9yAtWQItT5q4vYOmC9Q34XtSxvpLsLzVByd
        |rdvgXe0acjvMiTGcYBdjitawFYeLuz2s5mQAi4X1vcJqxBSBjG7X+1PiDqFFIid3
        |+6rIQtR3ho+Xqz/ucGglKxtn6m49wMHJABEBAAGJAR8EGAECAAkFAlVoyeYCGwwA
        |CgkQNjmHzWpAqldxFgf/SZIT1AiBAOLkqdWEObg0cU7n1YOXbj56sUeUCFxdbnl9
        |V2paf2SaMB6EEGLTk9PN0GG3hPyDkl4O6w3mn2J46uP8ecVaNvTSxoq2OmkMmD1H
        |/OSnF8a/jB6R1ODiAwekVuUMtAS7JiaAAcKcenG1f0XRKwQs52uavGXPgUuJbVtK
        |bB0SyLBhvGG8YIWTXRMHoJRt/Ls4JEuYaoBYqfV2eDn4WhW1LVuXP13gXixy0RiV
        |8rHs9aH8BAU7Dy0BBnaS3R9m8vtfdFxMI3/+1iGt0+xh/B4w++9oFE2DgyoZXUF8
        |mbjKYhiRPKNoj6Rn/mHUGcnuPlKvKP+1X5bObpDbQQ==
        |=TJUS
        |-----END PGP PUBLIC KEY BLOCK-----""".stripMargin

    for {
      publicKey <- PGPKeyAlg[IO].readPublicKey(key)
    } yield {
      /**
       * piping the public key to gpg yields the following:
       *
       * $ pbpaste  | gpg --show-keys --keyid-format long
       * pub   rsa2048/363987CD6A40AA57 2015-05-29 [SC]
       * ^^^^^^^^^^^^^^^^
       */
      assertEquals(publicKey.getKeyID, 0x363987CD6A40AA57L)
      assertEquals(publicKey.getAlgorithm, PublicKeyAlgorithmTags.RSA_GENERAL)
      assertEquals(publicKey.getBitStrength, 2048)
      assertEquals(publicKey.getCreationTime.toInstant, instant"2015-05-29T20:19:50Z")
    }
  }

  test("PGPKeyAlg should load an arbitrary armored PGP public key") {
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair

    forAllF { (keyR: Resource[IO, PGPPublicKey]) =>
      val testResource = keyR.evalMap { key =>
        val armored: IO[String] =
          (for {
            crypto <- Stream.resource(CryptoAlg.resource[IO])
            armored <- Stream.emits(key.getEncoded)
              .through(crypto.armor)
              .through(text.utf8.decode)
          } yield armored).compile.resource.string.use(s => s.pure[IO])

        for {
          armoredKey <- armored
          output <- PGPKeyAlg[IO].readPublicKey(armoredKey)
        } yield (key, output)
      }

      testResource.use { case (key, output) => IO {
        assertEquals(output.getKeyID, key.getKeyID)
        assertEquals(output.getAlgorithm, key.getAlgorithm)
        assertEquals(output.getBitStrength, key.getBitStrength)
        assertEquals(output.getCreationTime, key.getCreationTime)
      }
      }
    }
  }

  test("PGPKeyAlg should load an arbitrary armored PGP private key") {
    forAllF(genWeakKeyPair[IO], arbitrary[Array[Char]]) { (kp, passphrase) =>
      val testResource = kp.evalMap { keyPair =>
        val armoredKey =
          (for {
            crypto <- CryptoAlg.resource[IO]
            secretKey <- Resource.eval(IO.blocking {
              val sha1Calc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
              new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, keyPair, "identity", sha1Calc, null, null, new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256), new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC").build(passphrase))
            })
            armoredKey <-
              readOutputStream(4096) { os =>
                IO.blocking(secretKey.encode(os))
              }
                .through(crypto.armor)
                .through(text.utf8.decode)
                .compile
                .resource
                .string
          } yield armoredKey).use(s => s.pure[IO])

        for {
          ak <- armoredKey
          output <- PGPKeyAlg[IO].readPrivateKey(ak, passphrase)
        } yield (keyPair, output)
      }

      testResource.use { case (keyPair, output) => IO {
        assertEquals(output.getKeyID, keyPair.getPrivateKey.getKeyID)
      }
      }
    }
  }

  test("PGPKeyAlg should load an arbitrary armored PGP private key as a secret key collection") {
    forAllF(genWeakKeyPair[IO], arbitrary[Array[Char]], arbitrary[ChunkSize]) { (kp, passphrase, chunkSize) =>
      val testResource = kp.evalMap { keyPair =>
        val armoredKey =
          (for {
            crypto <- CryptoAlg.resource[IO]
            secretKey <- Resource.eval(IO.blocking {
              val sha1Calc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
              new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, keyPair, "identity", sha1Calc, null, null, new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256), new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC").build(passphrase))
            })
            armoredKey <-
              readOutputStream(chunkSize.value) { os =>
                IO.blocking(secretKey.encode(os))
              }
                .through(crypto.armor)
                .through(text.utf8.decode)
                .compile
                .resource
                .string
          } yield armoredKey).use(s => s.pure[IO])

        for {
          ak <- armoredKey
          output <- PGPKeyAlg[IO].readSecretKeyCollection(ak)
        } yield (keyPair, output)
      }

      testResource.use { case (keyPair, output) => IO {
        assertEquals(output.contains(keyPair.getPrivateKey.getKeyID), true)
        assertEquals(output.size(), 1)
      }
      }
    }
  }

  test("PGPKeyAlg should load an exported secret key into a key ring") {
    for {
      output <- PGPKeyAlg[IO].readSecretKeyCollection(TestKey())
      count <- Stream.fromBlockingIterator[IO](output.iterator().asScala, 1)
        .flatMap(ring => Stream.fromBlockingIterator[IO](ring.iterator().asScala, 1))
        .map(_ => 1)
        .compile
        .foldMonoid
    } yield {
      assertEquals(count, 2)
      assertEquals(output.contains(5958252092039458491L), true)
      assertEquals(output.contains(6117159660097923297L), true)
    }
  }

  test("PGPKeyAlg should infer the second Stream compiler type cleanly for IO") {
    val pgpKeyAlg = PGPKeyAlg[IO]

    val output = pgpKeyAlg.readPublicKey("")
    Assert(output.isInstanceOf[IO[PGPPublicKey]])
  }

  test("PGPKeyAlg should infer the second Stream compiler type cleanly for Resource") {
    val pgpKeyAlg = PGPKeyAlg[Resource[IO, *]]

    val output = pgpKeyAlg.readPublicKey("")
    Assert(output.isInstanceOf[Resource[IO, PGPPublicKey]])
  }
}
