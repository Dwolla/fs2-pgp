package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest._
import cats.syntax.all._
import com.dwolla.testutils._
import io.chrisdavenport.log4cats.Logger
import fs2._
import org.bouncycastle.bcpg.{HashAlgorithmTags, PublicKeyAlgorithmTags, SymmetricKeyAlgorithmTags}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.bouncycastle.openpgp.operator.jcajce.{JcaPGPContentSignerBuilder, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyEncryptorBuilder}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.flatspec._
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{CheckerAsserting, ScalaCheckPropertyChecks}

class PGPKeyAlgSpec
  extends FixtureAsyncFlatSpec
    with AsyncIOSpec
    with AsyncCatsResourceIO[Blocker]
    with Matchers
    with DateMatchers
    with ScalaCheckPropertyChecks
    with PgpArbitraries {
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
            secretKey <- Resource.liftF(blocker.delay {
              val sha1Calc: PGPDigestCalculator = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
              new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, keyPair, "identity", sha1Calc, null, null, new JcaPGPContentSignerBuilder(keyPair.getPublicKey.getAlgorithm, HashAlgorithmTags.SHA256), new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider("BC").build(passphrase))
            })
            armoredKey <-
              io.readOutputStream(blocker, defaultChunkSize) { os =>
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

  private implicit def ioCheckingAsserting[A]: CheckerAsserting[Resource[IO, A]] { type Result = IO[Unit] } =
    new ResourceCheckerAsserting[IO, A]
}
