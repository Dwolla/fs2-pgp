package com.dwolla.security.crypto

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.Compression.Zip
import com.dwolla.security.crypto.Encryption.Aes256
import com.dwolla.security.crypto.PgpLiteralDataPacketFormat.Binary
import fs2._
import fs2.io.{readOutputStream, writeOutputStream}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce._
import org.typelevel.log4cats.Logger

import java.io._
import scala.annotation.nowarn

trait Encrypt[F[_]] {
  final def encrypt(key: PGPPublicKey,
                    moreKeys: PGPPublicKey*): Pipe[F, Byte, Byte] =
    encrypt(NonEmptyList.of(key, moreKeys: _*), EncryptionConfig())

  final def encrypt(config: EncryptionConfig,
                    key: PGPPublicKey,
                    moreKeys: PGPPublicKey*): Pipe[F, Byte, Byte] =
    encrypt(NonEmptyList.of(key, moreKeys: _*), config)

  def encrypt(keys: NonEmptyList[PGPPublicKey],
              config: EncryptionConfig): Pipe[F, Byte, Byte]
}

object Encrypt {
  private type PgpEncryptionPipelineComponents = (PGPEncryptedDataGenerator, PGPCompressedDataGenerator, PGPLiteralDataGenerator)

  private def addKeys[F[_] : Sync](pgpEncryptedDataGenerator: PGPEncryptedDataGenerator,
                                   keys: NonEmptyList[PGPPublicKey]): F[Unit] =
    keys.traverse_ { key =>
      Sync[F].delay(pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key)))
    }

  /**
   * The order in which these armoring and generator resources are
   * created matters, because they need to be closed in the right
   * order for the encrypted data to be written out correctly.
   */
  private def pgpGenerators[F[_] : Sync](encryption: Encryption,
                                         compression: Compression,
                                        ): Resource[F, PgpEncryptionPipelineComponents] =
    for {
      pgpEncryptedDataGenerator <- Resource.make(Sync[F].blocking(new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(encryption.tag).setWithIntegrityPacket(true))))(g => Sync[F].blocking(g.close()))
      pgpCompressedDataGenerator <- Resource.make(Sync[F].blocking(new PGPCompressedDataGenerator(compression.tag)))(c => Sync[F].blocking(c.close()))
      pgpLiteralDataGenerator <- Resource.make(Sync[F].blocking(new PGPLiteralDataGenerator()))(g => Sync[F].blocking(g.close()))
    } yield (pgpEncryptedDataGenerator, pgpCompressedDataGenerator, pgpLiteralDataGenerator)

  /**
   * This method takes an <code>OutputStream</code> that will essentially be the
   * place where encrypted bytes are written, and returns an
   * OutputStream that accepts plaintext for encryption.
   *
   * The data flow is as follows:
   *
   * Plaintext -> "Literal Data" Packetizer -> Compressor -> Encryptor -> OutputStream provided by caller
   */
  private[crypto] def encryptingOutputStream[F[_] : Sync](keys: NonEmptyList[PGPPublicKey],
                                                          chunkSize: ChunkSize,
                                                          fileName: Option[String],
                                                          encryption: Encryption,
                                                          compression: Compression,
                                                          packetFormat: PgpLiteralDataPacketFormat,
                                                          outputStreamIntoWhichToWriteEncryptedBytes: OutputStream): Resource[F, OutputStream] =
    pgpGenerators[F](encryption, compression)
      .evalTap { case (pgpEncryptedDataGenerator, _, _) =>
        addKeys[F](pgpEncryptedDataGenerator, keys)
      }
      .evalMap { case (pgpEncryptedDataGenerator, pgpCompressedDataGenerator, pgpLiteralDataGenerator) =>
        for {
          now <- Clock[F].realTime.map(_.toMillis).map(new java.util.Date(_))
          encryptor <- Sync[F].blocking(pgpEncryptedDataGenerator.open(outputStreamIntoWhichToWriteEncryptedBytes, Array.ofDim[Byte](chunkSize.value)))
          compressor <- Sync[F].blocking(pgpCompressedDataGenerator.open(encryptor))
          literalizer <- Sync[F].blocking(pgpLiteralDataGenerator.open(compressor, packetFormat.tag, fileName.getOrElse(PGPLiteralData.CONSOLE), now, Array.ofDim[Byte](chunkSize.value)))
        } yield literalizer
      }

  @nowarn("""msg=parameter (?:value )?ev in method apply is never used""")
  def apply[F[_] : Async : Logger](implicit ev: BouncyCastleResource): Encrypt[F] = new Encrypt[F] {
    private val closeStreamsAfterUse = false

    override def encrypt(keys: NonEmptyList[PGPPublicKey], config: EncryptionConfig): Pipe[F, Byte, Byte] =
      _.through { bytes =>
        readOutputStream(config.chunkSize.value) { outputStreamToRead =>
          Logger[F].trace(s"${List.fill(keys.length)("🔑").mkString("")} encrypting input with ${keys.length} recipients") >>
            Stream
              .resource(encryptingOutputStream[F](keys, config.chunkSize, config.fileName, config.encryption, config.compression, config.packetFormat, outputStreamToRead))
              .flatMap(wos => bytes.chunkN(config.chunkSize.value).flatMap(Stream.chunk).through(writeOutputStream(wos.pure[F], closeStreamsAfterUse)))
              .compile
              .drain
        }
      }
  }
}

class EncryptionConfig private(val chunkSize: ChunkSize,
                               val fileName: Option[String],
                               val encryption: Encryption,
                               val compression: Compression,
                               val packetFormat: PgpLiteralDataPacketFormat,
                              ) {
  private def copy(chunkSize: ChunkSize = this.chunkSize,
                   fileName: Option[String] = this.fileName,
                   encryption: Encryption = this.encryption,
                   compression: Compression = this.compression,
                   packetFormat: PgpLiteralDataPacketFormat = this.packetFormat): EncryptionConfig =
    new EncryptionConfig(chunkSize, fileName, encryption, compression, packetFormat)

  def withChunkSize(chunkSize: ChunkSize): EncryptionConfig = copy(chunkSize = chunkSize)
  def withFileName(fileName: Option[String]): EncryptionConfig = copy(fileName = fileName)
  def withEncryption(encryption: Encryption): EncryptionConfig = copy(encryption = encryption)
  def withCompression(compression: Compression): EncryptionConfig = copy(compression = compression)
  def withPacketFormat(packetFormat: PgpLiteralDataPacketFormat): EncryptionConfig = copy(packetFormat = packetFormat)
}

object EncryptionConfig {
  def apply(): EncryptionConfig = new EncryptionConfig(defaultChunkSize, None, Aes256, Zip, Binary)
}
