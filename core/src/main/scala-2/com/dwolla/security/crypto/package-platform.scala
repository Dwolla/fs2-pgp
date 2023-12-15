package com.dwolla.security

import eu.timepit.refined.api.*
import eu.timepit.refined.auto.*
import eu.timepit.refined.predicates.all.*
import eu.timepit.refined.refineV
import eu.timepit.refined.types.all.*
import monix.newtypes.*
import org.typelevel.log4cats.Logger
import fs2.Stream

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
}
