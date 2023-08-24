package com.dwolla.security.crypto

import cats.effect._
import cats.syntax.all._
import com.dwolla.security.crypto.DecryptToInputStream._
import eu.timepit.refined.auto._
import fs2._
import fs2.io.{readInputStream, toInputStream}
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.operator.jcajce._
import org.typelevel.log4cats.{Logger, LoggerFactory}

import java.io._
import scala.annotation.nowarn

trait Decrypt[F[_]] {
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

object Decrypt {
  @nowarn("""msg=parameter (?:value )?ev in method apply is never used""")
  def apply[F[_] : Async : Logger : LoggerFactory](implicit ev: BouncyCastleResource): Decrypt[F] = new Decrypt[F] {
    import scala.jdk.CollectionConverters._
    private val objectIteratorChunkSize: ChunkSize = ChunkSize(1)
    private val fingerprintCalculator = new JcaKeyFingerprintCalculator

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
          readInputStream(Sync[F].blocking(pld.getDataStream), chunkSize.unrefined)

      def pgpEncryptedDataListToBytes(pedl: PGPEncryptedDataList): Stream[F, Byte] = {
        Logger[Stream[F, *]].trace(s"found ${pedl.size()} encrypted data packets") >>
          Stream.fromBlockingIterator[F](pedl.iterator().asScala, objectIteratorChunkSize.unrefined)
            .evalMap[F, Option[InputStream]] {
              case pbe: PGPPublicKeyEncryptedData =>
                // a key ID of 0L indicates a "hidden" recipient,
                // and we can't use that key ID to lookup the key
                val recipientKeyId = Option(pbe.getKeyID).filterNot(_ == 0)

                // if the recipient is identified, check if it exists in the key material we have
                // if it does, or if the recipient is undefined, try to decrypt.
                if (recipientKeyId.exists(DecryptToInputStream[F, A].hasKeyId(keylike, _)) || recipientKeyId.isEmpty)
                  pbe
                    .decryptToInputStream(keylike, recipientKeyId)
                    .map(_.pure[Option])
                    .recoverWith {
                      case ex: KeyRingMissingKeyException =>
                        Logger[F]
                          .trace(ex)(s"could not decrypt using key ${pbe.getKeyID}")
                          .as(None)
                      case ex: KeyMismatchException =>
                        Logger[F]
                          .trace(ex)(s"could not decrypt using key ${pbe.getKeyID}")
                          .as(None)
                    }
                else
                  none[InputStream].pure[F]

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
            objectIteratorChunkSize.unrefined
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
  }
}
