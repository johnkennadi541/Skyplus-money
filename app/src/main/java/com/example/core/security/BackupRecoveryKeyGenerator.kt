package com.example.core.security

import java.security.SecureRandom

/**
 * Creates a customer-held, portable recovery secret.
 *
 * The key is generated locally from 160 bits of SecureRandom entropy. It is
 * intentionally not derived from a Google account, device identifier, or server secret.
 * Callers must show it once, ask the customer to store it, and clear mutable copies.
 */
class BackupRecoveryKeyGenerator(
  private val secureRandom: SecureRandom = SecureRandom(),
) {
  fun generate(): CharArray {
    val entropy = ByteArray(ENTROPY_BYTES).also(secureRandom::nextBytes)
    return try {
      encodeBase32(entropy)
    } finally {
      entropy.fill(0)
    }
  }

  private fun encodeBase32(input: ByteArray): CharArray {
    val output = CharArray(OUTPUT_LENGTH)
    var buffer = 0
    var bitsInBuffer = 0
    var outputIndex = 0

    for (byte in input) {
      buffer = (buffer shl 8) or (byte.toInt() and 0xff)
      bitsInBuffer += 8
      while (bitsInBuffer >= 5) {
        bitsInBuffer -= 5
        output[outputIndex++] = ALPHABET[(buffer shr bitsInBuffer) and 0x1f]
      }
    }

    check(outputIndex == OUTPUT_LENGTH)
    return output
  }

  private companion object {
    private const val ENTROPY_BYTES = 20 // 160 bits
    private const val OUTPUT_LENGTH = 32 // 20 bytes encoded as unpadded Base32
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  }
}
