package com.dwolla.security.crypto

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import com.dwolla.security.crypto.Compression._
import com.dwolla.security.crypto.DecryptToInputStream._
import com.dwolla.security.crypto.Encryption._
import com.dwolla.security.crypto.PgpLiteralDataPacketFormat._
import fs2._
import fs2.io.{readInputStream, readOutputStream, toInputStream, writeOutputStream}
import org.bouncycastle.bcpg._
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce._
import org.typelevel.log4cats.{Logger, LoggerFactory, LoggerName}

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
              chunkSize: ChunkSize,
             ): Pipe[F, Byte, Byte]

  def decrypt(keyring: PGPSecretKeyRing,
              passphrase: Array[Char],
              chunkSize: ChunkSize,
             ): Pipe[F, Byte, Byte]

  def decrypt(keyring: PGPSecretKeyRingCollection,
              passphrase: Array[Char],
              chunkSize: ChunkSize,
             ): Pipe[F, Byte, Byte]

  def armor(chunkSize: ChunkSize = defaultChunkSize): Pipe[F, Byte, Byte]

  /* the rest of these definitions just provide default values for arguments */

  final def decrypt(key: PGPPrivateKey): Pipe[F, Byte, Byte] =
    decrypt(key, defaultChunkSize)

  final def decrypt(keyring: PGPSecretKeyRing): Pipe[F, Byte, Byte] =
    decrypt(keyring, Array.empty[Char], defaultChunkSize)

  final def decrypt(keyring: PGPSecretKeyRing, passphrase: Array[Char]): Pipe[F, Byte, Byte] =
    decrypt(keyring, passphrase, defaultChunkSize)

  final def decrypt(keyring: PGPSecretKeyRing, chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
    decrypt(keyring, Array.empty[Char], chunkSize)

  final def decrypt(keyring: PGPSecretKeyRingCollection): Pipe[F, Byte, Byte] =
    decrypt(keyring, Array.empty[Char], defaultChunkSize)

  final def decrypt(keyring: PGPSecretKeyRingCollection, passphrase: Array[Char]): Pipe[F, Byte, Byte] =
    decrypt(keyring, passphrase, defaultChunkSize)

  final def decrypt(keyring: PGPSecretKeyRingCollection, chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
    decrypt(keyring, Array.empty[Char], chunkSize)

}

object CryptoAlg extends CryptoAlgPlatform {
  private def addKey[F[_] : Sync](pgpEncryptedDataGenerator: PGPEncryptedDataGenerator, key: PGPPublicKey): F[Unit] =
    Sync[F].blocking(pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key)))

  private type PgpEncryptionPipelineComponents = (PGPEncryptedDataGenerator, PGPCompressedDataGenerator, PGPLiteralDataGenerator)

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
  private[crypto] def encryptingOutputStream[F[_] : Sync](key: PGPPublicKey,
                                                          chunkSize: ChunkSize,
                                                          fileName: Option[String],
                                                          encryption: Encryption,
                                                          compression: Compression,
                                                          packetFormat: PgpLiteralDataPacketFormat,
                                                          outputStreamIntoWhichToWriteEncryptedBytes: OutputStream): Resource[F, OutputStream] =
    pgpGenerators[F](encryption, compression)
      .evalTap { case (pgpEncryptedDataGenerator, _, _) =>
        addKey[F](pgpEncryptedDataGenerator, key)
      }
      .evalMap { case (pgpEncryptedDataGenerator, pgpCompressedDataGenerator, pgpLiteralDataGenerator) =>
        for {
          now <- Clock[F].realTime.map(_.toMillis).map(new java.util.Date(_))
          encryptor <- Sync[F].blocking(pgpEncryptedDataGenerator.open(outputStreamIntoWhichToWriteEncryptedBytes, Array.ofDim[Byte](chunkSize)))
          compressor <- Sync[F].blocking(pgpCompressedDataGenerator.open(encryptor))
          literalizer <- Sync[F].blocking(pgpLiteralDataGenerator.open(compressor, packetFormat.tag, fileName.getOrElse(PGPLiteralData.CONSOLE), now, Array.ofDim[Byte](chunkSize)))
        } yield literalizer
      }

  def apply[F[_] : Async : LoggerFactory](implicit loggerName: LoggerName = LoggerName("com.dwolla.security.crypto.CryptoAlg")): Resource[F, CryptoAlg[F]] =
    LoggerFactory[F]
      .create
      .toResource
      .flatMap { implicit logger =>
        CryptoAlg.withLogger[F]
      }

  def withLogger[F[_] : Async : Logger]: Resource[F, CryptoAlg[F]] =
    for {
      _ <- BouncyCastleResource[F]
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
          readOutputStream(chunkSize) { outputStreamToRead =>
            Stream
              .resource(encryptingOutputStream[F](key, chunkSize, fileName, encryption, compression, packetFormat, outputStreamToRead))
              .flatMap(wos => bytes.chunkN(chunkSize).flatMap(Stream.chunk).through(writeOutputStream(wos.pure[F], closeStreamsAfterUse)))
              .compile
              .drain
          }
        }

      private val objectIteratorChunkSize: ChunkSize = 1

      private def pgpInputStreamToByteStream[A: DecryptToInputStream[F, *]](keylike: A,
                                                                            chunkSize: ChunkSize): InputStream => Stream[F, Byte] = {
        def pgpCompressedDataToBytes(pcd: PGPCompressedData): Stream[F, Byte] =
          Logger[Stream[F, *]].trace("Found compressed data") >>
            pgpInputStreamToByteStream(keylike, chunkSize).apply(pcd.getDataStream)

        /*
         * Literal data is not to be further processed, so its contents
         * are the bytes to be read and output.
         */
        def pgpLiteralDataToBytes(pld: PGPLiteralData): Stream[F, Byte] =
          Logger[Stream[F, *]].trace(s"found literal data for file: ${pld.getFileName} and format: ${pld.getFormat}") >>
            readInputStream(Sync[F].blocking(pld.getDataStream), chunkSize)

        def pgpEncryptedDataListToBytes(pedl: PGPEncryptedDataList): Stream[F, Byte] = {
          Logger[Stream[F, *]].trace(s"found ${pedl.size()} encrypted data packets") >>
            Stream.fromBlockingIterator[F](pedl.iterator().asScala, objectIteratorChunkSize)
              .evalMap[F, Option[InputStream]] {
                case pbe: PGPPublicKeyEncryptedData =>
                  // a key ID of 0L indicates a "hidden" recipient,
                  // and we can't use that key ID to lookup the key
                  val recipientKeyId = Option(pbe.getKeyID).filterNot(_ == 0)

                  pbe.decryptToInputStream(keylike, recipientKeyId)
                    .map(_.pure[Option])
                    .recoverWith {
                      case ex: KeyRingMissingKeyException =>
                        Logger[F].trace(ex)(s"could not decrypt using key ${pbe.getKeyID}").as(None)
                    }

                case other =>
                  Logger[F].warn(EncryptionTypeError)(s"found wrong type of encrypted data: $other").as(None)
              }
              .unNone
              .head // if a value survived the unNone above, we have an InputStream we can work with, so move on
              .flatMap(pgpInputStreamToByteStream(keylike, chunkSize))
        }

        def ignore(s: String): Stream[F, Byte] =
          Logger[Stream[F, *]].trace(s"ignoring $s") >> Stream.empty

        pgpIS =>
          Logger[Stream[F, *]].trace("starting pgpInputStreamToByteStream") >>
            Stream.fromBlockingIterator[F](
              new PGPObjectFactory(pgpIS, fingerprintCalculator).iterator().asScala,
              objectIteratorChunkSize
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

      private def pipeToDecoderStream: Pipe[F, Byte, InputStream] =
        _.through(toInputStream[F])
          .evalTap(_ => Logger[F].trace("we have an InputStream containing the cryptotext"))
          .evalMap { cryptoIS =>
            Sync[F].blocking {
              PGPUtil.getDecoderStream(cryptoIS)
            }
          }

      override def decrypt(keyring: PGPSecretKeyRingCollection,
                           passphrase: Array[Char],
                           chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        _.through(pipeToDecoderStream)
          .flatMap(pgpInputStreamToByteStream((keyring, passphrase), chunkSize))

      override def decrypt(keyring: PGPSecretKeyRing,
                           passphrase: Array[Char],
                           chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        _.through(pipeToDecoderStream)
          .flatMap(pgpInputStreamToByteStream((keyring, passphrase), chunkSize))

      override def decrypt(key: PGPPrivateKey, chunkSize: ChunkSize): Pipe[F, Byte, Byte] =
        _.through(pipeToDecoderStream)
          .flatMap(pgpInputStreamToByteStream(key, chunkSize))

      private def writeToArmorer(armorer: OutputStream): Pipe[F, Byte, Unit] =
        _.through(writeOutputStream(armorer.pure[F], closeStreamsAfterUse))

      override def armor(chunkSize: ChunkSize): Pipe[F, Byte, Byte] = bytes =>
        readOutputStream(chunkSize) { out =>
          Stream.resource(Resource.fromAutoCloseable(Sync[F].blocking(new ArmoredOutputStream(out))))
            .flatMap(writeToArmorer(_)(bytes))
            .compile
            .drain
        }
    }

  @deprecated("use the variant with LoggerFactory instead", "0.4.0")
  override def apply[F[_]](F: Async[F], L: Logger[F]): Resource[F, CryptoAlg[F]] = CryptoAlg.withLogger[F](F, L)
}

// only kept to maintain binary compatibility
trait CryptoAlgPlatform {
  @deprecated("use the variant with LoggerFactory instead", "0.4.0")
  private[crypto] def apply[F[_]](ev1: Async[F], ev2: Logger[F]): Resource[F, CryptoAlg[F]] = CryptoAlg.withLogger[F](ev1, ev2)
}
