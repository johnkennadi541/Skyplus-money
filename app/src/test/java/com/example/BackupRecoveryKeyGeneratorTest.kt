package com.example

import com.example.core.security.BackupRecoveryKeyGenerator
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRecoveryKeyGeneratorTest {

  @Test
  fun `generates 160 bit customer recovery key in unambiguous Base32 alphabet`() {
    val key = BackupRecoveryKeyGenerator(DeterministicSecureRandom()).generate()
    val value = String(key)

    assertEquals(32, key.size)
    assertTrue(value.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
    assertFalse(value.any { it == 'I' || it == 'O' || it == '0' || it == '1' })
    key.fill('\u0000')
  }

  @Test
  fun `generates a fresh key for every setup`() {
    val random = CounterSecureRandom()
    val generator = BackupRecoveryKeyGenerator(random)

    val first = generator.generate()
    val second = generator.generate()

    assertNotEquals(String(first), String(second))
    first.fill('\u0000')
    second.fill('\u0000')
  }

  private class DeterministicSecureRandom : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) {
      bytes.indices.forEach { bytes[it] = it.toByte() }
    }
  }

  private class CounterSecureRandom : SecureRandom() {
    private var counter = 0
    override fun nextBytes(bytes: ByteArray) {
      bytes.indices.forEach { bytes[it] = (it + counter++).toByte() }
    }
  }
}
