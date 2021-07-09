package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.Compression._
import com.dwolla.security.crypto.Encryption._
import com.dwolla.security.crypto.PgpLiteralDataPacketFormat._
import org.typelevel.log4cats.Logger
import fs2._
import fs2.io.{readInputStream, toInputStream, writeOutputStream, readOutputStream}
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.jcajce._

import java.io._

trait CryptoAlg[F[_]] {
  def encrypt(key: PGPPublicKey,
              chunkSize: ChunkSize = defaultChunkSize,
              fileName: Option[String] = None,
              encryption: Encryption = Aes256,
              compression: Compression = Zip,
              packetFormat: PgpLiteralDataPacketFormat = Binary,
             ): Pipe[F, Byte, Byte]

  def decrypt(key: PGPPrivateKey,
              chunkSize: ChunkSize = defaultChunkSize,
             ): Pipe[F, Byte, Byte]

  def armor(chunkSize: ChunkSize = defaultChunkSize): Pipe[F, Byte, Byte]
}

object CryptoAlg {
  private def addKey[F[_] : Sync : ContextShift](blocker: Blocker)
                                 (pgpEncryptedDataGenerator: PGPEncryptedDataGenerator, key: PGPPublicKey): F[Unit] =
    blocker.delay(pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key)))

  private type PgpEncryptionPipelineComponents = (PGPEncryptedDataGenerator, PGPCompressedDataGenerator, PGPLiteralDataGenerator)

  /**
   * The order in which these armoring and generator resources are
   * created matters, because they need to be closed in the right
   * order for the encrypted data to be written out correctly.
   */
  private def pgpGenerators[F[_] : Sync : ContextShift](blocker: Blocker,
                                                        encryption: Encryption,
                                                        compression: Compression,
                                                       ): Resource[F, PgpEncryptionPipelineComponents] =
    for {
      pgpEncryptedDataGenerator <- Resource.make(blocker.delay(new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(encryption.tag).setWithIntegrityPacket(true))))(g => blocker.delay(g.close()))
      pgpCompressedDataGenerator <- Resource.make(blocker.delay(new PGPCompressedDataGenerator(compression.tag)))(c => blocker.delay(c.close()))
      pgpLiteralDataGenerator <- Resource.make(blocker.delay(new PGPLiteralDataGenerator()))(g => blocker.delay(g.close()))
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
  private[crypto] def encryptingOutputStream[F[_] : Sync : ContextShift : Clock](blocker: Blocker,
                                                                                 key: PGPPublicKey,
                                                                                 chunkSize: ChunkSize,
                                                                                 fileName: Option[String],
                                                                                 encryption: Encryption,
                                                                                 compression: Compression,
                                                                                 packetFormat: PgpLiteralDataPacketFormat,
                                                                                 outputStreamIntoWhichToWriteEncryptedBytes: OutputStream): Resource[F, OutputStream] =
    pgpGenerators[F](blocker, encryption, compression)
      .evalTap { case (pgpEncryptedDataGenerator, _, _) =>
        addKey[F](blocker)(pgpEncryptedDataGenerator, key)
      }
      .evalMap { case (pgpEncryptedDataGenerator, pgpCompressedDataGenerator, pgpLiteralDataGenerator) =>
        for {
          now <- Clock[F].realTime(scala.concurrent.duration.MILLISECONDS).map(new java.util.Date(_))
          encryptor <- blocker.delay(pgpEncryptedDataGenerator.open(outputStreamIntoWhichToWriteEncryptedBytes, Array.ofDim[Byte](chunkSize.value)))
          compressor <- blocker.delay(pgpCompressedDataGenerator.open(encryptor))
          literalizer <- blocker.delay(pgpLiteralDataGenerator.open(compressor, packetFormat.tag, fileName.getOrElse(PGPLiteralData.CONSOLE), now, Array.ofDim[Byte](chunkSize.value)))
        } yield literalizer
      }

  def apply[F[_] : ConcurrentEffect : ContextShift : Clock : Logger](blocker: Blocker,
                                                                     removeOnClose: Boolean = true): Resource[F, CryptoAlg[F]] =
    for {
      _ <- BouncyCastleResource[F](blocker, removeOnClose)
    } yield new CryptoAlg[F] {
      import scala.jdk.CollectionConverters._

      private implicit val SLogger: Logger[Stream[F, *]] = Logger[F].mapK(Stream.functionKInstance[F])
      private val fingerprintCalculator = new JcaKeyFingerprintCalculator
      private val closeStreamsAfterUse = false

      override def encrypt(key: PGPPublicKey,
                           chunkSize: ChunkSize,
                           fileName: Option[String] = None,
                           encryption: Encryption = Aes256,
                           compression: Compression = Zip,
                           packetFormat: PgpLiteralDataPacketFormat = Binary,
                          ): Pipe[F, Byte, Byte] =
        _.through { bytes =>
          readOutputStream(blocker, chunkSize.value) { outputStreamToRead =>
            Stream
              .resource(encryptingOutputStream[F](blocker, key, chunkSize, fileName, encryption, compression, packetFormat, outputStreamToRead))
              .flatMap(wos => bytes.chunkN(chunkSize.value).flatMap(Stream.chunk).through(writeOutputStream(wos.pure[F], blocker, closeStreamsAfterUse)))
              .compile
              .drain
          }
        }

      private def pgpInputStreamToByteStream(key: PGPPrivateKey,
                                             chunkSize: ChunkSize): InputStream => Stream[F, Byte] = {
        def pgpCompressedDataToBytes(pcd: PGPCompressedData): Stream[F, Byte] =
          Logger[Stream[F, *]].trace("Found compressed data") >>
            pgpInputStreamToByteStream(key, chunkSize)(pcd.getDataStream)

        /*
         * Literal data is not to be further processed, so its contents
         * are the bytes to be read and output.
         */
        def pgpLiteralDataToBytes(pld: PGPLiteralData): Stream[F, Byte] =
          Logger[Stream[F, *]].trace(s"found literal data for file: ${pld.getFileName} and format: ${pld.getFormat}") >>
            readInputStream(blocker.delay(pld.getDataStream), chunkSize.value, blocker)

        def pgpEncryptedDataListToBytes(pedl: PGPEncryptedDataList): Stream[F, Byte] = {
          Logger[Stream[F, *]].trace(s"found ${pedl.size()} encrypted data packets") >>
            Stream.fromIterator[F](pedl.iterator().asScala)
              .evalMap {
                case pbe: PGPPublicKeyEncryptedData =>
                  if (key.getKeyID != pbe.getKeyID) KeyMismatchException(key.getKeyID, pbe.getKeyID).raiseError[F, InputStream]
                  else blocker.delay(pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(key)))
                case other =>
                  Logger[F].error(EncryptionTypeError)(s"found wrong type of encrypted data: $other") >>
                    EncryptionTypeError.raiseError[F, InputStream]
              }
              .flatMap(pgpInputStreamToByteStream(key, chunkSize))
        }

        def ignore(s: String): Stream[F, Byte] =
          Logger[Stream[F, *]].trace(s"ignoring $s") >> Stream.empty

        pgpIS =>
          Logger[Stream[F, *]].trace("starting pgpInputStreamToByteStream") >>
            Stream.fromBlockingIterator[F](
              blocker,
              new PGPObjectFactory(pgpIS, fingerprintCalculator).iterator().asScala
            )
              .flatMap {
                case _: PGPSignatureList => ignore("PGPSignatureList")
                case _: PGPSecretKeyRing => ignore("PGPSecretKeyRing")
                case _: PGPPublicKeyRing => ignore("PGPPublicKeyRing")
                case _: PGPPublicKey => ignore("PGPPublicKey")
                case x: PGPCompressedData => pgpCompressedDataToBytes(x)
                case x: PGPLiteralData => pgpLiteralDataToBytes(x)
                case x: PGPEncryptedDataList => pgpEncryptedDataListToBytes(x)
                case _: PGPOnePassSignatureList => ignore("PGPOnePassSignatureList")
                case _: PGPMarker => ignore("PGPMarker")
                case other => Logger[Stream[F, *]].warn(s"found unexpected $other") >> Stream.empty
              }
      }

      override def decrypt(key: PGPPrivateKey,
                           chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        _.through(toInputStream[F])
          .evalTap(_ => Logger[F].trace("we have an InputStream containing the cryptotext"))
          .evalMap { cryptoIS =>
            blocker.delay {
              PGPUtil.getDecoderStream(cryptoIS)
            }
          }
          .flatMap(pgpInputStreamToByteStream(key, chunkSize))

      private def writeToArmorer(armorer: OutputStream): Pipe[F, Byte, Unit] =
        _.through(writeOutputStream(armorer.pure[F], blocker, closeStreamsAfterUse))

      override def armor(chunkSize: ChunkSize): Pipe[F, Byte, Byte] = bytes =>
        readOutputStream(blocker, chunkSize.value) { out =>
          Stream.resource(Resource.fromAutoCloseableBlocking(blocker)(blocker.delay(new ArmoredOutputStream(out))))
            .flatMap(writeToArmorer(_)(bytes))
            .compile
            .drain
        }

    }
}
