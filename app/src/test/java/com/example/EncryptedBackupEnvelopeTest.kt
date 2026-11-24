package com.example

import com.example.core.security.EncryptedBackupEnvelope
import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupEnvelopeTest {

  @Test
  fun `backup payload round trips and rejects wrong secret account and tampering`() {
    val engine = EncryptedBackupEnvelope()
    val payload = "encrypted database snapshot and metadata".toByteArray()
    val secret = "correct horse battery staple".toCharArray()
    val envelope = engine.encrypt(payload, secret, "firebase-uid-001")

    assertArrayEquals(payload, engine.decrypt(envelope, secret, "firebase-uid-001"))
    assertThrows(GeneralSecurityException::class.java) {
      engine.decrypt(envelope, "different recovery phrase".toCharArray(), "firebase-uid-001")
    }
    assertThrows(GeneralSecurityException::class.java) {
      engine.decrypt(envelope, secret, "firebase-uid-002")
    }

    val tampered = envelope.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }
    assertThrows(GeneralSecurityException::class.java) {
      engine.decrypt(tampered, secret, "firebase-uid-001")
    }
  }

  @Test
  fun `backup envelope rejects truncated or unknown payloads`() {
    val engine = EncryptedBackupEnvelope()
    assertThrows(IllegalArgumentException::class.java) {
      engine.decrypt(byteArrayOf(1, 2, 3), "correct horse battery staple".toCharArray(), "firebase-uid")
    }
  }

  @Test
  fun `backup encryption rejects weak recovery secrets`() {
    val engine = EncryptedBackupEnvelope()
    assertThrows(IllegalArgumentException::class.java) {
      engine.encrypt(byteArrayOf(1), "short".toCharArray(), "firebase-uid")
    }
  }
}
