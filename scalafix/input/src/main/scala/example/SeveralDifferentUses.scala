package example
/*
rule = com.dwolla.security.crypto.V04to05
*/
import cats.effect.*
import com.dwolla.security.crypto.*
import eu.timepit.refined.auto.*
import org.bouncycastle.openpgp.PGPPublicKey
import org.typelevel.log4cats.LoggerFactory

object SeveralDifferentUses {
  private def key: PGPPublicKey = ???
  private def chunkSize: ChunkSize = ???
  private implicit def loggerFactory: LoggerFactory[IO] = ???

  CryptoAlg[IO].evalMap { alg =>

    // all arguments set
    alg.encrypt(key, chunkSize, Option("filename"), Encryption.Aes256, Compression.Bzip2, PgpLiteralDataPacketFormat.Utf8)
    alg.encrypt(key, tagChunkSize(42), Option("filename"), Encryption.Aes256, Compression.Bzip2, PgpLiteralDataPacketFormat.Utf8)
    alg.encrypt(new PGPPublicKey(null, null), tagChunkSize(42), Option("filename"), Encryption.Aes256, Compression.Bzip2, PgpLiteralDataPacketFormat.Utf8)

    // all arguments set with named parameters
    alg.encrypt(key = key, chunkSize = chunkSize, fileName = Option("filename"), encryption = Encryption.Aes256, compression = Compression.Bzip2, packetFormat = PgpLiteralDataPacketFormat.Utf8)
    alg.encrypt(key = key, chunkSize = {
      tagChunkSize(42)
    }, fileName = Option("filename"), encryption = Encryption.Aes256, compression = Compression.Bzip2, packetFormat = PgpLiteralDataPacketFormat.Utf8)
    alg.encrypt(key, chunkSize = chunkSize, fileName = Option("filename"), encryption = Encryption.Aes256, compression = Compression.Bzip2, packetFormat = PgpLiteralDataPacketFormat.Utf8)

    // random assortments
    alg.encrypt(key, {
      tagChunkSize(42)
    }, Option("filename"))
    alg.encrypt(key, fileName = Option("filename"), chunkSize = chunkSize)
    alg.encrypt(key = key, encryption = Encryption.Aes256, packetFormat = PgpLiteralDataPacketFormat.Utf8)
    alg.encrypt(key, fileName = Option("filename"), compression = Compression.Bzip2)

    ???
  }
}
