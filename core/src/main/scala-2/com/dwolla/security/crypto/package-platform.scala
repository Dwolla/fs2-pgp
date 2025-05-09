package com.dwolla.security

import eu.timepit.refined.api.*
import eu.timepit.refined.auto.*
import eu.timepit.refined.predicates.all.*
import eu.timepit.refined.refineV
import eu.timepit.refined.types.all.*
import fs2.Stream
import monix.newtypes.*
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.typelevel.log4cats.Logger

import java.io.InputStream

package object crypto {
  type ChunkSize = ChunkSize.Type
  object ChunkSize extends NewtypeWrapped[PosInt]

  def attemptTagChunkSize(pi: Int): Either[String, ChunkSize] = refineV[Positive](pi).map(ChunkSize(_))

  val defaultChunkSize: ChunkSize = ChunkSize(4096)
  private[crypto] val objectIteratorChunkSize: ChunkSize = ChunkSize(1)

  implicit class RefinedNewtypeOps[RNT](val refinedNewtype: RNT) extends AnyVal {
    def unrefined[X, R[_, _], T, P](implicit
                                    ex: HasExtractor.Aux[RNT, X],
                                    x: X =:= R[T, P],
                                    rt: RefType[R],
                                   ): T = {
      rt.unwrap(x(ex.extract(refinedNewtype)))
    }
  }

  private[crypto] implicit def SLogger[F[_] : Logger]: Logger[Stream[F, *]] = Logger[F].mapK(Stream.functionKInstance[F])
  private[crypto] implicit def toPGPPublicKeyEncryptedDataOps(pbed: PGPPublicKeyEncryptedData): PGPPublicKeyEncryptedDataOps = new PGPPublicKeyEncryptedDataOps(pbed)
}

package crypto {
  private[crypto] class PGPPublicKeyEncryptedDataOps(val pbed: PGPPublicKeyEncryptedData) extends AnyVal {
    def decryptToInputStream[F[_], A](input: A, maybeKeyId: Option[Long])
                                     (implicit D: DecryptToInputStream[F, A]): F[InputStream] =
      DecryptToInputStream[F, A].decryptToInputStream(input, maybeKeyId)(pbed)
  }
}
