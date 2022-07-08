package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.io.{readOutputStream, writeOutputStream}
import org.bouncycastle.bcpg._

import java.io._
import scala.annotation.nowarn

trait Armor[F[_]] {
  def armor(chunkSize: ChunkSize): Pipe[F, Byte, Byte]

  final def armor: Pipe[F, Byte, Byte] = armor(defaultChunkSize)
}

object Armor {
  @nowarn("""msg=parameter (?:value )?ev in method apply is never used""")
  def apply[F[_] : Async](implicit ev: BouncyCastleResource): Armor[F] = new Armor[F] {
    private val closeStreamsAfterUse = false

    override def armor(chunkSize: ChunkSize): Pipe[F, Byte, Byte] = bytes =>
      readOutputStream(chunkSize.value) { out =>
        Stream.resource(Resource.fromAutoCloseable(Sync[F].blocking(new ArmoredOutputStream(out))))
          .flatMap { armorer =>
            bytes.through(writeOutputStream(armorer.pure[F].widen[OutputStream], closeStreamsAfterUse))
          }
          .compile
          .drain
      }
  }
}
