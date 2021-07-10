package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest._
import cats.syntax.all._
import com.dwolla.testutils._
import eu.timepit.refined.auto._
import org.typelevel.log4cats.Logger
import fs2._
import fs2.io.readOutputStream
import org.bouncycastle.bcpg.{HashAlgorithmTags, PublicKeyAlgorithmTags, SymmetricKeyAlgorithmTags}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.flatspec._
import scala.jdk.CollectionConverters._

class PGPKeyAlgSpec
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[Blocker]
    with Fs2PgpSpec
    with DateMatchers {
  private implicit val L: Logger[IO] = NoOpLogger[IO]()
  private implicit def arbKeyPair[F[_] : Sync : ContextShift : Clock]: Arbitrary[Resource[F, PGPKeyPair]] = arbStrongKeyPair[F]

  override def resource: Resource[IO, Blocker] = Blocker[IO]

  behavior of "PGPKeyAlg"

  it should "load a PGPPublicKey from armored public key" in { blocker =>
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
      publicKey <- PGPKeyAlg[IO](blocker).readPublicKey(key)
    } yield {
      /**
       * piping the public key to gpg yields the following:
       *
       * $ pbpaste  | gpg --show-keys --keyid-format long
       * pub   rsa2048/363987CD6A40AA57 2015-05-29 [SC]
       * ^^^^^^^^^^^^^^^^
       */
      publicKey.getKeyID should be(0x363987CD6A40AA57L)
      publicKey.getAlgorithm should be(PublicKeyAlgorithmTags.RSA_GENERAL)
      publicKey.getBitStrength should be(2048)
      publicKey.getCreationTime should beTheSameInstantAs("2015-05-29T20:19:50Z")
    }
  }

  it should "load an arbitrary armored PGP public key" in { blocker =>
    forAll { (keyR: Resource[IO, PGPPublicKey]) =>
      keyR.evalMap { key =>
        val armored: IO[String] =
          (for {
            crypto <- Stream.resource(CryptoAlg[IO](blocker, removeOnClose = false))
            armored <- Stream.emits(key.getEncoded)
              .through(crypto.armor())
              .through(text.utf8Decode)
          } yield armored).compile.resource.string.use(s => s.pure[IO])

        for {
          armoredKey <- armored
          output <- PGPKeyAlg[IO](blocker).readPublicKey(armoredKey)
        } yield {
          output.getKeyID should be(key.getKeyID)
          output.getAlgorithm should be(key.getAlgorithm)
          output.getBitStrength should be(key.getBitStrength)
          output.getCreationTime should be(key.getCreationTime)
        }
      }
    }
  }

  it should "load an arbitrary armored PGP private key" in { blocker =>
    forAll(arbKeyPair[IO].arbitrary, arbitrary[Array[Char]]) { (kp, passphrase) =>
      kp.evalMap { keyPair =>
        val armoredKey =
          (for {
            crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
            secretKey <- Resource.eval(blocker.delay {
              val sha1Calc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
              new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, keyPair, "identity", sha1Calc, null, null, new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256), new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC").build(passphrase))
            })
            armoredKey <-
              readOutputStream(blocker, 4096) { os =>
                blocker.delay(secretKey.encode(os))
              }
                .through(crypto.armor())
                .through(text.utf8Decode)
                .compile
                .resource
                .string
          } yield armoredKey).use(s => s.pure[IO])

        for {
          ak <- armoredKey
          output <- PGPKeyAlg[IO](blocker).readPrivateKey(ak, passphrase)
        } yield {
          output.getKeyID should be(keyPair.getPrivateKey.getKeyID)
        }
      }
    }
  }

  it should "load an arbitrary armored PGP private key as a secret key collection" in { blocker =>
    forAll(arbKeyPair[IO].arbitrary, arbitrary[Array[Char]]) { (kp, passphrase) =>
      kp.evalMap { keyPair =>
        val armoredKey =
          (for {
            crypto <- CryptoAlg[IO](blocker, removeOnClose = false)
            secretKey <- Resource.eval(blocker.delay {
              val sha1Calc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
              new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, keyPair, "identity", sha1Calc, null, null, new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256), new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC").build(passphrase))
            })
            armoredKey <-
              readOutputStream(blocker, 4096) { os =>
                blocker.delay(secretKey.encode(os))
              }
                .through(crypto.armor())
                .through(text.utf8Decode)
                .compile
                .resource
                .string
          } yield armoredKey).use(s => s.pure[IO])

        for {
          ak <- armoredKey
          output <- PGPKeyAlg[IO](blocker).readSecretKeyCollection(ak)
        } yield {
          output.contains(keyPair.getPrivateKey.getKeyID) should be(true)
          output.size() should be(1)
        }
      }
    }
  }

  it should "load an exported secret key into a key ring" in { blocker =>
    for {
      output <- PGPKeyAlg[IO](blocker).readSecretKeyCollection(TestKey())
      count <- Stream.fromIterator[IO](output.iterator().asScala)
        .flatMap(ring => Stream.fromIterator[IO](ring.iterator().asScala))
        .map(_ => 1)
        .compile
        .foldMonoid
    } yield {
      count should be(2)
      output.contains(5958252092039458491L) should be(true)
      output.contains(6117159660097923297L) should be(true)
    }
  }

  it should "infer the second Stream compiler type cleanly for IO" in { blocker =>
    val pgpKeyAlg = PGPKeyAlg[IO](blocker)

    val output = pgpKeyAlg.readPublicKey("")
    output shouldBe an [IO[_]]
  }

  it should "infer the second Stream compiler type cleanly for Resource" in { blocker =>
    val pgpKeyAlg = PGPKeyAlg[Resource[IO, *]](blocker)

    val output = pgpKeyAlg.readPublicKey("")
    output shouldBe a [Resource[*[_], _]]
    output.use(_ => IO.unit) shouldBe an [IO[_]]
  }
}

object TestKey {
  def apply(): String =
    """-----BEGIN PGP PRIVATE KEY BLOCK-----
      |
      |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
      |=29wW
      |-----END PGP PRIVATE KEY BLOCK-----
      |""".stripMargin
}
