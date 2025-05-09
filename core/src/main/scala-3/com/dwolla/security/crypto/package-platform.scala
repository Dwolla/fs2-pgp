package com.dwolla.security.crypto

import eu.timepit.refined.api.*
import eu.timepit.refined.predicates.all.*
import eu.timepit.refined.refineV
import eu.timepit.refined.types.all.*
import monix.newtypes.*
import org.typelevel.log4cats.Logger
import fs2.Stream
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData

import java.io.InputStream

type ChunkSize = ChunkSize.Type
object ChunkSize extends NewtypeWrapped[PosInt]

def attemptTagChunkSize(pi: Int): Either[String, ChunkSize] = refineV[Positive](pi).map(ChunkSize(_))

val defaultChunkSize: ChunkSize = ChunkSize(PosInt.unsafeFrom(4096)) // TODO refined macro
private[crypto] val objectIteratorChunkSize: ChunkSize = ChunkSize(PosInt.unsafeFrom(1)) // TODO refined macro

private[crypto] implicit class RefinedNewtypeOps[RNT](val refinedNewtype: RNT) extends AnyVal {
  def unrefined[X, R[_, _], T, P](implicit
                                  ex: HasExtractor.Aux[RNT, X],
                                  x: X =:= R[T, P],
                                  rt: RefType[R],
                                 ): T = {
    rt.unwrap(x(ex.extract(refinedNewtype)))
  }
}

private[crypto] implicit def SLogger[F[_] : Logger]: Logger[Stream[F, *]] = Logger[F].mapK(Stream.functionKInstance[F])

extension (pbed: PGPPublicKeyEncryptedData) {
  private[crypto] def decryptToInputStream[F[_], A](input: A, maybeKeyId: Option[Long])
                                   (implicit D: DecryptToInputStream[F, A]): F[InputStream] =
    DecryptToInputStream[F, A].decryptToInputStream(input, maybeKeyId)(pbed)
}
