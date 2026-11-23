package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the customer's recovery secret only as Android-Keystore-wrapped ciphertext on this device.
 *
 * The plaintext key is never sent to a server or written to Drive. The customer must save the
 * one-time displayed key separately so it can be entered on a replacement device.
 */
@Singleton
class BackupRecoveryKeyStore @Inject constructor(
  context: Context,
) {
  private val preferences: SharedPreferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val backend: KeyWrappingBackend = AndroidKeyStoreBackend()

  @Synchronized
  fun store(recoveryKey: CharArray) {
    require(recoveryKey.size == RECOVERY_KEY_LENGTH) { "Invalid recovery key length" }
    require(recoveryKey.all { it in RECOVERY_KEY_ALPHABET }) { "Invalid recovery key characters" }
    val plainBytes = String(recoveryKey).toByteArray(StandardCharsets.US_ASCII)
    try {
      val (ciphertext, iv) = backend.encrypt(plainBytes)
      check(
        preferences.edit()
          .putString(KEY_CIPHERTEXT, ciphertext)
          .putString(KEY_IV, iv)
          .commit(),
      ) { "Unable to persist protected recovery key" }
      check(
        preferences.getString(KEY_CIPHERTEXT, null) == ciphertext &&
          preferences.getString(KEY_IV, null) == iv,
      ) { "Protected recovery key persistence verification failed" }
    } finally {
      plainBytes.fill(0)
    }
  }

  @Synchronized
  fun load(): CharArray? {
    val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
    val iv = preferences.getString(KEY_IV, null)
    if (ciphertext == null && iv == null) return null
    check(!ciphertext.isNullOrBlank() && !iv.isNullOrBlank()) {
      "Protected recovery key metadata is incomplete"
    }
    val plaintext = backend.decrypt(ciphertext, iv)
    try {
      val key = String(plaintext, StandardCharsets.US_ASCII)
      check(key.length == RECOVERY_KEY_LENGTH && key.all { it in RECOVERY_KEY_ALPHABET }) {
        "Protected recovery key failed validation"
      }
      return key.toCharArray()
    } finally {
      plaintext.fill(0)
    }
  }

  @Synchronized
  fun hasKey(): Boolean =
    preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV)

  private companion object {
    private const val PREFERENCES_NAME = "skyplus_backup_recovery_vault"
    private const val KEY_CIPHERTEXT = "wrapped_recovery_key"
    private const val KEY_IV = "wrapped_recovery_key_iv"
    private const val RECOVERY_KEY_LENGTH = 32
    private const val RECOVERY_KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  }
}
