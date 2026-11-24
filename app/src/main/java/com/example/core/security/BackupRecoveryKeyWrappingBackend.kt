package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local wrapping key dedicated to the recovery-key vault.
 *
 * A distinct Android Keystore alias and authenticated purpose string prevent ciphertext from
 * another SKYPLUS MONEY vault (for example, the SQLCipher passphrase vault) being accepted here.
 */
class BackupRecoveryKeyWrappingBackend : KeyWrappingBackend {
  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
      val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      generator.init(
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .setRandomizedEncryptionRequired(true)
          .build(),
      )
      generator.generateKey()
    }
    return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
      ?: throw UnrecoverableKeyException("Recovery-key Keystore entry is unavailable")
  }

  override fun encrypt(raw: ByteArray): Pair<String, String> {
    require(raw.size == RECOVERY_KEY_BYTES) { "Unexpected recovery-key byte length" }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    cipher.updateAAD(AAD)
    val iv = cipher.iv
    var ciphertext: ByteArray? = null
    return try {
      val encryptedBytes = cipher.doFinal(raw)
      ciphertext = encryptedBytes
      Base64.encodeToString(encryptedBytes, Base64.NO_WRAP) to
        Base64.encodeToString(iv, Base64.NO_WRAP)
    } finally {
      ciphertext?.fill(0)
      iv.fill(0)
    }
  }

  override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray {
    // Bound locally persisted metadata before Base64 decoding allocates arrays.
    require(encryptedBase64.length == RECOVERY_CIPHERTEXT_BASE64_LENGTH) {
      "Wrapped recovery-key ciphertext has an invalid encoded length"
    }
    require(ivBase64.length == RECOVERY_IV_BASE64_LENGTH) { "Recovery-key IV has an invalid encoded length" }
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
      throw UnrecoverableKeyException("Recovery-key Keystore master key is missing")
    }
    val key = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
      ?: throw UnrecoverableKeyException("Recovery-key Keystore entry is invalid")
    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
    var ciphertext: ByteArray? = null
    try {
      val decodedCiphertext = Base64.decode(encryptedBase64, Base64.NO_WRAP)
      ciphertext = decodedCiphertext
      require(iv.size == GCM_IV_BYTES) { "Recovery-key IV is invalid" }
      require(decodedCiphertext.size == RECOVERY_KEY_BYTES + GCM_TAG_BYTES) {
        "Wrapped recovery-key ciphertext has an invalid length"
      }
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.updateAAD(AAD)
      return cipher.doFinal(decodedCiphertext)
    } finally {
      iv.fill(0)
      ciphertext?.fill(0)
    }
  }

  private companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "skyplus_backup_recovery_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val RECOVERY_KEY_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val RECOVERY_IV_BASE64_LENGTH = 16
    private const val RECOVERY_CIPHERTEXT_BASE64_LENGTH = 64
    private const val GCM_TAG_BYTES = 16
    private const val GCM_TAG_BITS = 128
    private val AAD = "SKYPLUS_MONEY|BACKUP_RECOVERY_KEY|v1".toByteArray(Charsets.US_ASCII)
  }
}
