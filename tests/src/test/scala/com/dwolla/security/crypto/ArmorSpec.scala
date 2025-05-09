package com.dwolla.security.crypto

import cats.effect.*
import com.dwolla.testutils.*
import fs2.*
import munit.catseffect.IOFixture
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.bcpg.*
import org.bouncycastle.openpgp.*
import org.scalacheck.*
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.ByteArrayOutputStream

class ArmorSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PgpArbitraries
    with CryptoArbitraries {

  private implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val resource: IOFixture[CryptoAlg[IO]] = ResourceSuiteLocalFixture("CryptoAlg[IO]", CryptoAlg.resource[IO])

  override def munitFixtures: Seq[IOFixture[?]] = List(resource)

  test("CryptoAlg should support armoring a PGP value") {
    val crypto = resource()
    implicit val arbKeyPair: Arbitrary[Resource[IO, PGPKeyPair]] = arbWeakKeyPair[IO]

    forAllF(genPgpBytes[IO]) { (bytesR: Resource[IO, Array[Byte]]) =>
      val testResource =
        for {
          bytes <- bytesR
          armored <- Stream.emits(bytes).through(crypto.armor).through(text.utf8.decode).compile.resource.string
          expected <- Resource.eval {
            for {
              out <- IO(new ByteArrayOutputStream())
              _ <- Resource.fromAutoCloseable(IO(new ArmoredOutputStream(out))).evalMap(arm => IO(arm.write(bytes))).use(_ => IO.unit)
              s <- IO(new String(out.toByteArray))
            } yield s
          }
        } yield (armored, expected)

      testResource.use { case (armored, expected) => IO {
        assertEquals(armored, expected)
      }
      }
    }
  }
}
