package com.example.core.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable authenticated encryption for Drive backup payloads.
 *
 * The recovery secret is used only to derive a per-backup AES-256 key. The
 * authenticated Firebase UID is included as additional authenticated data so
 * a backup cannot be restored into a different signed-in account.
 */
class EncryptedBackupEnvelope(
  private val secureRandom: SecureRandom = SecureRandom(),
) {
  fun encrypt(plaintext: ByteArray, recoverySecret: CharArray, uid: String): ByteArray {
    require(plaintext.isNotEmpty()) { "Backup payload is empty" }
    require(uid.isNotBlank()) { "Authenticated user is required" }
    require(recoverySecret.size >= MIN_SECRET_LENGTH) {
      "Backup recovery secret must contain at least $MIN_SECRET_LENGTH characters"
    }

    val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
    val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
    var keyBytes: ByteArray? = null
    var ciphertext: ByteArray? = null
    return try {
      val derivedKey = deriveKey(recoverySecret, salt)
      keyBytes = derivedKey
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
      cipher.updateAAD(additionalData(uid))
      val encryptedBytes = cipher.doFinal(plaintext)
      ciphertext = encryptedBytes
      ByteBuffer.allocate(HEADER_BYTES + salt.size + iv.size + encryptedBytes.size)
        .put(MAGIC)
        .put(VERSION)
        .put(salt)
        .put(iv)
        .put(encryptedBytes)
        .array()
    } finally {
      keyBytes?.fill(0)
      ciphertext?.fill(0)
      salt.fill(0)
      iv.fill(0)
    }
  }

  @Throws(GeneralSecurityException::class)
  fun decrypt(envelope: ByteArray, recoverySecret: CharArray, uid: String): ByteArray {
    require(uid.isNotBlank()) { "Authenticated user is required" }
    require(recoverySecret.size >= MIN_SECRET_LENGTH) { "Invalid backup recovery secret" }
    require(envelope.size >= HEADER_BYTES + SALT_BYTES + IV_BYTES + TAG_BYTES) {
      "Backup envelope is incomplete"
    }

    val buffer = ByteBuffer.wrap(envelope)
    val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
    require(magic.contentEquals(MAGIC)) { "Unrecognized backup format" }
    require(buffer.get() == VERSION) { "Unsupported backup version" }
    val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
    val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
    val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
    var keyBytes: ByteArray? = null
    return try {
      val derivedKey = deriveKey(recoverySecret, salt)
      keyBytes = derivedKey
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
      cipher.updateAAD(additionalData(uid))
      cipher.doFinal(ciphertext)
    } catch (error: AEADBadTagException) {
      throw GeneralSecurityException("Backup authentication failed; check recovery secret and account", error)
    } finally {
      keyBytes?.fill(0)
      magic.fill(0)
      salt.fill(0)
      iv.fill(0)
      ciphertext.fill(0)
    }
  }

  private fun deriveKey(secret: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, KEY_BITS)
    return try {
      SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun additionalData(uid: String): ByteArray =
    "$AAD_PREFIX|$VERSION|$uid".toByteArray(Charsets.UTF_8)

  private companion object {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG_BYTES = 16
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MIN_SECRET_LENGTH = 12
    private const val VERSION: Byte = 1
    private const val AAD_PREFIX = "SKYPLUS_MONEY_DRIVE_BACKUP"
    private val MAGIC = byteArrayOf(0x53, 0x50, 0x4D, 0x42, 0x4B, 0x55, 0x50, 0x31)
    private const val HEADER_BYTES = 9
  }
}
