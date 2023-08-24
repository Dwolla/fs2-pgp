package com.dwolla.security

import eu.timepit.refined.api.RefType
import eu.timepit.refined.auto.*
import eu.timepit.refined.predicates.all.Positive
import eu.timepit.refined.refineV
import eu.timepit.refined.types.all.*
import fs2.Stream
import monix.newtypes.*
import org.bouncycastle.openpgp.PGPLiteralData
import org.typelevel.log4cats.Logger

package object crypto {
  type ChunkSize = ChunkSize.Type
  object ChunkSize extends NewtypeWrapped[PosInt]

  def attemptTagChunkSize(pi: Int): Either[String, ChunkSize] = refineV[Positive](pi).map(ChunkSize(_))

  val defaultChunkSize: ChunkSize = ChunkSize(4096)

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

package crypto {
  sealed trait Encryption {
    val tag: Int
  }

  object Encryption {
    //    case object Idea extends Encryption { override val tag: Int = 1 }
    //    case object TripleDes extends Encryption { override val tag: Int = 2 }
    //    case object Cast5 extends Encryption { override val tag: Int = 3 }
    //    case object Blowfish extends Encryption { override val tag: Int = 4 }
    //    case object Safer extends Encryption { override val tag: Int = 5 }
    //    case object Des extends Encryption { override val tag: Int = 6 }
    //    case object Aes128 extends Encryption { override val tag: Int = 7 }
    //    case object Aes192 extends Encryption { override val tag: Int = 8 }
    case object Aes256 extends Encryption { override val tag: Int = 9 }
    //    case object TwoFish extends Encryption { override val tag: Int = 10 }
    //    case object Camellia128 extends Encryption { override val tag: Int = 11 }
    //    case object Camellia192 extends Encryption { override val tag: Int = 12 }
    //    case object Camellia256 extends Encryption { override val tag: Int = 13 }
  }

  sealed trait Compression {
    val tag: Int
  }

  object Compression {
    case object Uncompressed extends Compression { override val tag: Int = 0 }
    case object Zip extends Compression { override val tag: Int = 1 }
    case object Zlib extends Compression { override val tag: Int = 2 }
    case object Bzip2 extends Compression { override val tag: Int = 3 }
  }

  sealed trait PgpLiteralDataPacketFormat {
    val tag: Char
  }

  object PgpLiteralDataPacketFormat {
    case object Binary extends PgpLiteralDataPacketFormat { override val tag: Char = PGPLiteralData.BINARY }
    case object Text extends PgpLiteralDataPacketFormat { override val tag: Char = PGPLiteralData.TEXT }
    case object Utf8 extends PgpLiteralDataPacketFormat { override val tag: Char = PGPLiteralData.UTF8 }
  }
}
