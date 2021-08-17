package com.dwolla.security.crypto

import com.dwolla.security.crypto.Compression._
import com.dwolla.security.crypto.Encryption._
import com.dwolla.security.crypto.PgpLiteralDataPacketFormat._
import fs2._
import org.bouncycastle.openpgp._

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

object CryptoAlg extends CryptoAlgPlatform
