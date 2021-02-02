package com.dwolla.security.crypto

import cats.effect._
import fs2.{Pure, Stream}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AsyncFlatSpec

class StreamableOutputStreamSpec
  extends AsyncFlatSpec
    with Fs2PgpSpec {

  private implicit def arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    for {
      count <- Gen.chooseNum(0, 20000000)
      moreBytes <- Gen.listOfN(count, arbitrary[Byte])
    } yield Stream.emits(moreBytes)
  }

  behavior of "StreamableOutputStream"

  it should "stream out the bytes written in" in {
    forAll { bytes: Stream[Pure, Byte] =>
      val expected = bytes.compile.toVector

      Blocker[IO].evalMap { blocker =>
        StreamableOutputStream.readOutputStream[IO](blocker) { os =>
          bytes.chunks.evalMap(c => IO(os.write(c.toArray))).compile.drain
        }
          .compile
          .toVector
          .map(_ should be(expected))
      }
    }
  }
}
