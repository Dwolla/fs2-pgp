package example

import cats.effect.*
import com.dwolla.security.crypto.*
import eu.timepit.refined.auto.*
import org.bouncycastle.openpgp.PGPPublicKey
import org.typelevel.log4cats.LoggerFactory

object SeveralDifferentUses {
  private def key: PGPPublicKey = ???
  private def chunkSize: ChunkSize = ???
  private implicit def loggerFactory: LoggerFactory[IO] = ???

  CryptoAlg.resource[IO].evalMap { alg =>

    // all arguments set
    alg.encrypt(EncryptionConfig().withChunkSize(chunkSize).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)
    alg.encrypt(EncryptionConfig().withChunkSize(ChunkSize(42)).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)
    alg.encrypt(EncryptionConfig().withChunkSize(ChunkSize(42)).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), new PGPPublicKey(null, null))

    // all arguments set with named parameters
    alg.encrypt(EncryptionConfig().withChunkSize(chunkSize).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)
    alg.encrypt(EncryptionConfig().withChunkSize({
      ChunkSize(42)
    }).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)
    alg.encrypt(EncryptionConfig().withChunkSize(chunkSize).withFileName(Option("filename")).withEncryption(Encryption.Aes256).withCompression(Compression.Bzip2).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)

    // random assortments
    alg.encrypt(EncryptionConfig().withChunkSize({
      ChunkSize(42)
    }).withFileName(Option("filename")), key)
    alg.encrypt(EncryptionConfig().withFileName(Option("filename")).withChunkSize(chunkSize), key)
    alg.encrypt(EncryptionConfig().withEncryption(Encryption.Aes256).withPacketFormat(PgpLiteralDataPacketFormat.Utf8), key)
    alg.encrypt(EncryptionConfig().withFileName(Option("filename")).withCompression(Compression.Bzip2), key)

    ???
  }
}
