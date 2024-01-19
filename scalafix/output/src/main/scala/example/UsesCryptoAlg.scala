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
    CryptoAlg.resource[IO].evalMap { alg =>
      Stream
        .empty
        .through(alg.encrypt(EncryptionConfig().withChunkSize(ChunkSize(untaggedChunkSize)).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key))
        .through(alg.armor)
        .compile
        .drain
    }
}
