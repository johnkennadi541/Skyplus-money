package com.example

import android.content.Context
import com.example.core.security.BackupRecoveryKeyStore
import com.example.core.security.KeyWrappingBackend
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupRecoveryKeyStoreTest {
  private lateinit var preferences: android.content.SharedPreferences
  private lateinit var store: BackupRecoveryKeyStore

  @Before
  fun setUp() {
    preferences = RuntimeEnvironment.getApplication()
      .getSharedPreferences("recovery_key_store_test", Context.MODE_PRIVATE)
    preferences.edit().clear().commit()
    store = BackupRecoveryKeyStore(preferences, TestWrappingBackend())
  }

  @Test
  fun `stores only wrapped data and loads the same recovery key`() {
    val key = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

    store.store(key)
    val restored = store.load()

    assertTrue(store.hasKey())
    assertArrayEquals(key, restored)
    assertFalse(preferences.all.values.any { it == String(key) })
    restored?.fill(Char.MIN_VALUE)
    key.fill(Char.MIN_VALUE)
  }

  @Test
  fun `returns null when no recovery key is stored`() {
    assertNull(store.load())
    assertFalse(store.hasKey())
  }

  @Test
  fun `rejects incomplete wrapped key metadata without replacing it`() {
    preferences.edit().putString("wrapped_recovery_key", "ciphertext-only").commit()

    assertThrows(IllegalStateException::class.java) { store.load() }
    assertEquals("ciphertext-only", preferences.getString("wrapped_recovery_key", null))
  }

  @Test
  fun `rejects tampered wrapped ciphertext`() {
    val key = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    store.store(key)
    val original = preferences.getString("wrapped_recovery_key", null)!!
    val bytes = Base64.getDecoder().decode(original)
    bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
    preferences.edit().putString("wrapped_recovery_key", Base64.getEncoder().encodeToString(bytes)).commit()

    assertThrows(AEADBadTagException::class.java) { store.load() }
    key.fill(Char.MIN_VALUE)
  }

  @Test
  fun `rejects invalid recovery key before persisting anything`() {
    val invalid = "INVALID-RECOVERY-KEY".toCharArray()

    assertThrows(IllegalArgumentException::class.java) { store.store(invalid) }
    assertTrue(preferences.all.isEmpty())
    invalid.fill(Char.MIN_VALUE)
  }

  private class TestWrappingBackend : KeyWrappingBackend {
    override fun encrypt(raw: ByteArray): Pair<String, String> {
      val encrypted = raw.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
      val tag = MessageDigest.getInstance("SHA-256").digest(encrypted)
      val payload = encrypted + tag
      return Base64.getEncoder().encodeToString(payload) to "test-iv"
    }

    override fun decrypt(encryptedBase64: String, ivBase64: String): ByteArray {
      if (ivBase64 != "test-iv") throw AEADBadTagException("Invalid test IV")
      val payload = try {
        Base64.getDecoder().decode(encryptedBase64)
      } catch (error: IllegalArgumentException) {
        throw AEADBadTagException("Invalid test ciphertext").also { it.initCause(error) }
      }
      if (payload.size != 64) throw AEADBadTagException("Invalid test ciphertext length")
      val encrypted = payload.copyOfRange(0, 32)
      val suppliedTag = payload.copyOfRange(32, 64)
      val expectedTag = MessageDigest.getInstance("SHA-256").digest(encrypted)
      if (!MessageDigest.isEqual(suppliedTag, expectedTag)) {
        throw AEADBadTagException("Tampered test ciphertext")
      }
      return encrypted.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }
  }
}
