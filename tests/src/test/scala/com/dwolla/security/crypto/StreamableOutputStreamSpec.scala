package com.dwolla.security.crypto

import cats.effect._
import cats.effect.testing.scalatest._
import cats.syntax.all._
import com.dwolla.security.crypto.StreamableOutputStream.readOutputStream
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import eu.timepit.refined.types.all._
import fs2.{Pure, Stream}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.FixtureAsyncFlatSpec

import scala.concurrent.duration.DurationInt

class StreamableOutputStreamSpec
  extends FixtureAsyncFlatSpec
    with CatsResourceIO[Blocker]
    with Fs2PgpSpec {

  override def resource: Resource[IO, Blocker] = Blocker[IO]

  private implicit def arbBytes: Arbitrary[Stream[Pure, Byte]] = Arbitrary {
    Gen.chooseNum(1, 1048576).flatMap { count =>
      genStreamOfN[Byte](count)
    }
  }

  private def genStreamOfN[T: Arbitrary](count: Int): Gen[Stream[Pure, T]] =
    Gen.listOfN(count, arbitrary[T]).map(Stream.emits(_))

  private val genSmallPosInt: Gen[PosInt] =
    chooseRefinedNum[Refined, Int, Positive](1, 1024)

  private implicit val arbChunkSize: Arbitrary[ChunkSize] = Arbitrary {
    chooseRefinedNum[Refined, Int, Positive](1024, 4096).map(tagChunkSize)
  }

  behavior of "StreamableOutputStream"

  it should "stream out the bytes written in" in { blocker =>
    forAll { (bytes: Stream[Pure, Byte], chunkSize: ChunkSize) =>
      val expected = bytes.compile.toVector

      readOutputStream[IO](blocker, chunkSize) { os =>
        bytes.chunks
          .evalMap(c => IO(os.write(c.toArray)))
          .compile
          .drain >> IO(os.close())
      }
        .compile
        .toVector
        .map(_ should be(expected))
    }
  }

  it should "work when the writing thread dies" in { blocker =>
    readOutputStream(blocker, 1024) { os =>
      blocker.delay[IO, Unit] {
        val t = new Thread(
          () => os.write(123)
        )
        t.start()
        t.join()
        Thread.sleep(1000)
      }
    }
      .compile
      .toList
      .map(_ should be(List(123.toByte)))
  }

  it should "chunk the bytes read according to the given chunk size" in { blocker =>
    forAll { chunkSize: ChunkSize =>
      forAll(genStreamOfN[Byte](chunkSize.value + 1)) { (bytes: Stream[Pure, Byte]) =>
        readOutputStream[IO](blocker, chunkSize) { os =>
          bytes.evalMap(b => IO(os.write(b.toInt)))
            .compile
            .drain
        }
          .chunks
          .map(_.size)
          .compile
          .to(Set)
          .map(_ should contain(chunkSize.value))
      }
    }
  }

  it should "fail the stream if the writing effect fails" in { blocker =>
    forAll { (ex: Exception, chunkSize: ChunkSize) =>
      readOutputStream[IO](blocker, chunkSize) { _ =>
        ex.raiseError[IO, Unit]
      }
        .attempt
        .compile
        .lastOrError
        .map(_ should be(Left(ex)))
    }
  }

  it should "end the stream if the writing effect calls close" in { blocker =>
    forAll { (bytes: Stream[Pure, Byte], chunkSize: ChunkSize) =>
      val expected = bytes.compile.toVector

      readOutputStream[IO](blocker, chunkSize) { os =>
        bytes.chunks.evalMap(c => IO(os.write(c.toArray))).compile.drain >>
          IO(os.close()) >>
          IO.never
      }
        .compile
        .toVector
        .map(_ should be(expected))
        .timeout(60.seconds)
    }
  }

  it should "fail with a NullPointerException if a null array is passed" in { blocker =>
    forAll { (off: NonNegInt, len: NonNegInt, chunkSize: ChunkSize) =>
      readOutputStream[IO](blocker, chunkSize) { os =>
        IO(os.write(null, off.value, len.value))
      }
        .attempt
        .compile
        .lastOrError
        .map(_ should matchPattern {
          case Left(NullPointerException(msg)) if msg == "the passed Array[Byte] must not be null" =>
        })
    }
  }

  it should "fail with an IndexOutOfBoundsException if the offset is less than 0" in { blocker =>
    forAll { (bytes: Array[Byte], off: NegInt, len: NonNegInt, chunkSize: ChunkSize) =>
      readOutputStream[IO](blocker, chunkSize) { os =>
        IO(os.write(bytes, off.value, len.value))
      }
        .attempt
        .compile
        .lastOrError
        .map(_ should matchPattern {
          case Left(IndexOutOfBoundsException(msg)) if msg == s"offset must be greater than or equal to 0, but $off < 0" =>
        })
    }
  }

  it should "fail with an IndexOutOfBoundsException if the offset is greater than the array length" in { blocker =>
    forAll { (bytes: Array[Byte], len: NonNegInt, chunkSize: ChunkSize) =>
      val off = bytes.length + 1
      readOutputStream[IO](blocker, chunkSize) { os =>
        IO(os.write(bytes, off, len.value))
      }
        .attempt
        .compile
        .lastOrError
        .map(_ should matchPattern {
          case Left(IndexOutOfBoundsException(msg)) if msg == s"offset must be less than or equal to the buffer length ($off > ${bytes.length})" =>
        })
    }
  }

  it should "fail with an IndexOutOfBoundsException if the length is less than 0" in { blocker =>
    forAll { (bytes: Array[Byte], len: NegInt, chunkSize: ChunkSize) =>
      val off = bytes.length
      readOutputStream[IO](blocker, chunkSize) { os =>
        IO(os.write(bytes, off, len.value))
      }
        .attempt
        .compile
        .lastOrError
        .map(_ should matchPattern {
          case Left(IndexOutOfBoundsException(msg)) if msg == s"the number of bytes to be written must be greater than or equal to 0, but $len < 0" =>
        })
    }
  }

  it should "fail with an IndexOutOfBoundsException if the offset + length is greater than the buffer size" in { blocker =>
    forAll(genSmallPosInt, genSmallPosInt) { (off, len) =>
      forAll(Gen.containerOfN[Array, Byte](off.value + len.value - 1, arbitrary[Byte])) { bytes: Array[Byte] =>
        forAll { chunkSize: ChunkSize =>
          readOutputStream[IO](blocker, chunkSize) { os =>
            IO(os.write(bytes, off.value, len.value))
          }
            .attempt
            .compile
            .lastOrError
            .map(_ should matchPattern {
              case Left(IndexOutOfBoundsException(msg)) if msg == s"the offset plus number of bytes to be written must be less than or equal to the length of the buffer, but ($off + $len) > ${bytes.length}" =>
            })
        }
      }
    }
  }
}

object NullPointerException {
  def unapply(arg: NullPointerException): Option[String] = arg.getMessage.some
}

object IndexOutOfBoundsException {
  def unapply(arg: IndexOutOfBoundsException): Option[String] = arg.getMessage.some
}
