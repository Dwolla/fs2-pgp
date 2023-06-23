package example

import cats.effect.*
import com.dwolla.security.crypto.*
import eu.timepit.refined.types.all.PosInt
import fs2.*
import org.bouncycastle.openpgp.PGPPublicKey
import org.typelevel.log4cats.LoggerFactory

object UsesCryptoAlg extends ResourceApp.Simple {
  private def key: PGPPublicKey = ???
  private def untaggedChunkSize: PosInt = ???
  private implicit def loggerFactory: LoggerFactory[IO] = ???

  override def run: Resource[IO, Unit] =
    CryptoAlg[IO].evalMap { alg =>
      Stream
        .empty
        .through(alg.encrypt(
          key,
          tagChunkSize(untaggedChunkSize),
          Option("filename"),
          Encryption.Aes256,
          Compression.Bzip2,
          PgpLiteralDataPacketFormat.Utf8,
        ))
        .through(alg.armor())
        .compile
        .drain
    }
}
