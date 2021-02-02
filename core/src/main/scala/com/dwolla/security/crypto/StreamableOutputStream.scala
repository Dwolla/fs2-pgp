package com.dwolla.security.crypto

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2._
import fs2.concurrent.Queue

import java.io.OutputStream

abstract class StreamableOutputStream[F[_]] extends OutputStream with AutoCloseable {
  val stream: Stream[F, Byte]
}

object StreamableOutputStream {
  def readOutputStream[F[_] : ConcurrentEffect : ContextShift](blocker: Blocker)
                                                              (f: OutputStream => F[Unit]): Stream[F, Byte] =
    Stream.resource(StreamableOutputStream[F](blocker)).flatMap { os =>
      os.stream.concurrently(Stream.eval(f(os)) >> Stream.eval(Sync[F].delay(os.close())))
    }

  def apply[F[_] : ConcurrentEffect : ContextShift](blocker: Blocker): Resource[F, StreamableOutputStream[F]] =
    Resource.fromAutoCloseableBlocking(blocker)(acquireSES[F])

  private def acquireSES[F[_] : ConcurrentEffect]: F[StreamableOutputStream[F]] =
    Queue.boundedNoneTerminated[F, Chunk[Byte]](1).map { queue =>
      new StreamableOutputStream[F] {
        override val stream: Stream[F, Byte] =
          queue.dequeue.flatMap(Stream.chunk)

        override def write(b: Int): Unit =
          queue.enqueue1(Option(Chunk(b.toByte))).toIO.unsafeRunSync()

        override def write(b: Array[Byte]): Unit =
          queue.enqueue1(Option(Chunk.array(b))).toIO.unsafeRunSync()

        override def write(b: Array[Byte], off: Int, len: Int): Unit =
          if (null == b) throw new NullPointerException("the passed Array[Byte] must not be null")
          else if (off < 0) throw new IndexOutOfBoundsException(s"offset must be greater than or equal to 0, but $off < 0")
          else if (off > b.length) throw new IndexOutOfBoundsException(s"offset must be less than or equal to the buffer length ($off > ${b.length})")
          else if (len < 0) throw new IndexOutOfBoundsException(s"the number of bytes to be written must be greater than or equal to 0, but $len < 0")
          else if ((off + len) > b.length) throw new IndexOutOfBoundsException(s"the offset plus number of bytes to be written must be less than or equal to the length of the buffer, but ($off + $len) > ${b.length}")
          else if ((off + len) < 0) throw new IndexOutOfBoundsException(s"the offset plus the number of bytes to be written must be greater than or equal to 0, but ($off + $len) < 0")
          else if (0 == len) ()
          else write(b.slice(off, off + len))

        override def close(): Unit =
          queue.enqueue1(None).toIO.unsafeRunSync()
      }
    }
}
