package com.dwolla.security.crypto

import cats.effect._
import cats.effect.concurrent.Ref
import cats.effect.syntax.all._
import cats.syntax.all._
import eu.timepit.refined.types.all._
import fs2._
import fs2.concurrent.Queue
import scodec.bits.ByteVector

import java.io.OutputStream
import java.util.ConcurrentModificationException

abstract class StreamableOutputStream[F[_]] extends OutputStream with AutoCloseable {
  val stream: Stream[F, Byte]
}

object StreamableOutputStream {
  def readOutputStream[F[_] : ConcurrentEffect : ContextShift](blocker: Blocker, chunkSize: PosInt)
                                                              (f: OutputStream => F[Unit]): Stream[F, Byte] =
    Stream.resource(StreamableOutputStream[F](blocker, chunkSize)).flatMap { os =>
      os.stream.concurrently(Stream.eval(f(os)) >> Stream.eval(Sync[F].delay(os.close())))
    }

  def apply[F[_] : ConcurrentEffect : ContextShift](blocker: Blocker, chunkSize: PosInt): Resource[F, StreamableOutputStream[F]] =
    Resource.fromAutoCloseableBlocking(blocker)(acquireSES[F](chunkSize))

  private def acquireSES[F[_] : ConcurrentEffect](chunkSize: PosInt): F[StreamableOutputStream[F]] =
    for {
      ref <- Ref.of[F, ByteVector](ByteVector.empty)
      queue <- Queue.boundedNoneTerminated[F, Chunk[Byte]](chunkSize.value)
    } yield new StreamableOutputStream[F] {
      override val stream: Stream[F, Byte] =
        queue.dequeue.flatMap(Stream.chunk)

      override def write(b: Int): Unit = write(ByteVector(b.toByte))

      override def write(b: Array[Byte]): Unit = write(ByteVector(b))

      override def write(b: Array[Byte], off: Int, len: Int): Unit =
        if (null == b) throw new NullPointerException("the passed Array[Byte] must not be null")
        else if (off < 0) throw new IndexOutOfBoundsException(s"offset must be greater than or equal to 0, but $off < 0")
        else if (off > b.length) throw new IndexOutOfBoundsException(s"offset must be less than or equal to the buffer length ($off > ${b.length})")
        else if (len < 0) throw new IndexOutOfBoundsException(s"the number of bytes to be written must be greater than or equal to 0, but $len < 0")
        else if ((off + len) > b.length) throw new IndexOutOfBoundsException(s"the offset plus number of bytes to be written must be less than or equal to the length of the buffer, but ($off + $len) > ${b.length}")
        else if (0 == len) ()
        else write(ByteVector(b, off, len))

      private def appendBytes(bytes: ByteVector)
                             (buf: ByteVector): (ByteVector, Option[ByteVector]) = {
        val appended = buf ++ bytes

        if (appended.size >= chunkSize.value) {
          appended.splitAt(chunkSize.value.toLong).swap.map(_.some)
        } else
          (appended, None)
      }

      private def enqueueByteVector(buf: ByteVector): F[Unit] =
          queue.enqueue1(Chunk.array(buf.toArray).some)

      private def enqueueIfModificationSucceeded: Option[Option[ByteVector]] => F[Unit] = {
        case Some(Some(buf)) => enqueueByteVector(buf)
        case Some(None) => ().pure[F]
        case None => new ConcurrentModificationException("Failed to modify the ByteVector. Failing since ordering cannot be guaranteed").raiseError[F, Unit]
      }

      private def getAndClearBuffer(buf: ByteVector): (ByteVector, Option[ByteVector]) =
        if (buf.nonEmpty)
          (ByteVector.empty, buf.some)
        else
          (ByteVector.empty, none[ByteVector])

      private val onFlush: F[Unit] =
        for {
          modified <- ref.modify(getAndClearBuffer).map(_.some)
          _ <- enqueueIfModificationSucceeded(modified)
        } yield ()

      private val onClose: F[Unit] =
        for {
          _ <- onFlush
          _ <- queue.enqueue1(None)
        } yield ()

      private def write(b: ByteVector): Unit =
        ref.modify(appendBytes(b)).map(_.some)
          .flatMap(enqueueIfModificationSucceeded)
          .toIO
          .unsafeRunSync()

      override def flush(): Unit =
        onFlush
          .toIO
          .unsafeRunSync()

      override def close(): Unit =
        onClose
          .toIO
          .unsafeRunSync()
    }
}
