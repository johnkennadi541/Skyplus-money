package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.core.database.DatabaseContract
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyWrappingBackend {
  fun encrypt(raw: ByteArray): Pair<String, String>
  fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray
}

class AndroidKeyStoreBackend : KeyWrappingBackend {
  private fun getOrCreateMasterKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
      val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      val spec =
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      keyGenerator.init(spec)
      keyGenerator.generateKey()
    }
    val entry =
      keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        ?: throw IllegalStateException("Keystore entry '$KEY_ALIAS' is missing or not a SecretKeyEntry")
    return entry.secretKey
  }

  override fun encrypt(raw: ByteArray): Pair<String, String> {
    val secretKey = getOrCreateMasterKey()
    val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(raw)
    return Pair(
      Base64.encodeToString(ciphertext, Base64.NO_WRAP),
      Base64.encodeToString(iv, Base64.NO_WRAP),
    )
  }

  override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
      throw UnrecoverableKeyException("Master key '$KEY_ALIAS' not found in AndroidKeyStore")
    }
    val entry =
      keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        ?: throw UnrecoverableKeyException("Keystore entry '$KEY_ALIAS' is invalid")
    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
    val ciphertext = Base64.decode(encryptedBase64, Base64.NO_WRAP)
    val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
    val spec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, spec)
    return cipher.doFinal(ciphertext)
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "skyplus_sqlcipher_master_key"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
  }
}

class DatabasePassphraseProvider(
  private val context: Context,
  private val keyBackend: KeyWrappingBackend = AndroidKeyStoreBackend(),
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  private val databaseFileProvider: () -> File = {
    context.getDatabasePath(DatabaseContract.DATABASE_NAME)
  },
) {

  /**
   * Retrieves or generates a 256-bit passphrase protected by Android Keystore.
   * Fail-closed: never returns an unpersisted passphrase and never replaces an
   * existing wrapped key or database key on decryption/Keystore failure.
   */
  @Synchronized
  fun getOrCreatePassphrase(): ByteArray {
    val existingEncrypted = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
    val existingIv = prefs.getString(KEY_IV, null)

    if ((existingEncrypted == null) != (existingIv == null)) {
      throw DatabaseSecurityException(
        reason = PassphraseFailureReason.CORRUPTED_VAULT_METADATA,
        isRecoverable = false,
        message = "Database key metadata is incomplete; refusing to overwrite existing vault.",
      )
    }

    if (existingEncrypted != null && existingIv != null) {
      val decrypted =
        try {
          keyBackend.decrypt(existingEncrypted, existingIv)
        } catch (e: AEADBadTagException) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEY_CORRUPTED,
            isRecoverable = false,
            message = "Wrapped database passphrase failed integrity verification.",
            cause = e,
          )
        } catch (e: KeyPermanentlyInvalidatedException) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEY_CORRUPTED,
            isRecoverable = false,
            message = "Android Keystore master key was permanently invalidated.",
            cause = e,
          )
        } catch (e: UnrecoverableKeyException) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEY_CORRUPTED,
            isRecoverable = false,
            message = "Android Keystore master key cannot be recovered for existing wrapped passphrase.",
            cause = e,
          )
        } catch (e: IllegalArgumentException) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEY_CORRUPTED,
            isRecoverable = false,
            message = "Stored wrapped database passphrase is malformed.",
            cause = e,
          )
        } catch (e: Exception) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEYSTORE_DECRYPT_FAILED,
            isRecoverable = true,
            message = "Temporary Keystore failure while decrypting database passphrase.",
            cause = e,
          )
        }

      if (decrypted.size != PASSPHRASE_LENGTH_BYTES) {
        // Invalid plaintext must never remain resident after validation fails.
        decrypted.fill(0)
        throw DatabaseSecurityException(
          reason = PassphraseFailureReason.KEY_CORRUPTED,
          isRecoverable = false,
          message = "Decrypted database passphrase has unexpected length.",
        )
      }
      return decrypted
    }

    // Refuse to generate a replacement key if an existing database file already exists on disk.
    val dbFile = databaseFileProvider()
    if (dbFile.exists() && dbFile.length() > 0L) {
      throw DatabaseSecurityException(
        reason = PassphraseFailureReason.MISSING_KEY_FOR_EXISTING_DATABASE,
        isRecoverable = false,
        message = "Existing encrypted database detected without stored passphrase; refusing to create a replacement key.",
      )
    }

    // Fresh install: generate a 256-bit passphrase, wrap it, persist synchronously,
    // and verify the round trip. Clear every temporary copy that is not transferred
    // to the SQLCipher factory.
    val rawPassphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
    SecureRandom().nextBytes(rawPassphrase)
    var ownershipTransferred = false
    try {
      val (encryptedBase64, ivBase64) =
        try {
          keyBackend.encrypt(rawPassphrase)
        } catch (e: Exception) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.KEYSTORE_ENCRYPT_FAILED,
            isRecoverable = true,
            message = "Failed to wrap new database passphrase with Android Keystore.",
            cause = e,
          )
        }

      val committed =
        prefs.edit()
          .putString(KEY_ENCRYPTED_PASSPHRASE, encryptedBase64)
          .putString(KEY_IV, ivBase64)
          .commit()

      if (!committed) {
        throw DatabaseSecurityException(
          reason = PassphraseFailureReason.PERSISTENCE_FAILED,
          isRecoverable = true,
          message = "Failed to persist wrapped database passphrase synchronously.",
        )
      }

      val persistedEnc = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
      val persistedIv = prefs.getString(KEY_IV, null)
      if (persistedEnc != encryptedBase64 || persistedIv != ivBase64) {
        throw DatabaseSecurityException(
          reason = PassphraseFailureReason.PERSISTENCE_VERIFICATION_FAILED,
          isRecoverable = true,
          message = "Persisted wrapped database passphrase could not be verified after commit.",
        )
      }

      val verifiedPassphrase =
        try {
          keyBackend.decrypt(persistedEnc, persistedIv)
        } catch (e: Exception) {
          throw DatabaseSecurityException(
            reason = PassphraseFailureReason.PERSISTENCE_VERIFICATION_FAILED,
            isRecoverable = true,
            message = "Round-trip verification of persisted database passphrase failed.",
            cause = e,
          )
        }

      val matches = try {
        MessageDigest.isEqual(verifiedPassphrase, rawPassphrase)
      } finally {
        verifiedPassphrase.fill(0)
      }
      if (!matches) {
        throw DatabaseSecurityException(
          reason = PassphraseFailureReason.PERSISTENCE_VERIFICATION_FAILED,
          isRecoverable = false,
          message = "Verified database passphrase does not match generated key.",
        )
      }

      // Ownership passes to SupportFactory, configured to clear this byte array
      // immediately after SQLCipher opens the database.
      ownershipTransferred = true
      return rawPassphrase
    } finally {
      if (!ownershipTransferred) rawPassphrase.fill(0)
    }
  }

  companion object {
    const val PREFS_NAME = "skyplus_db_secure_vault"
    const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_pass"
    const val KEY_IV = "enc_db_iv"
    private const val PASSPHRASE_LENGTH_BYTES = 32
  }
}
