package com.example.core.security

/**
 * Security baseline contract for SKYPLUS MONEY.
 * Phase 2 implements Android Keystore-backed key wrapping and SQLCipher database key provisioning.
 */
interface SecurityPolicy {
  val requiresMandatoryAuthentication: Boolean
  val allowsGuestMode: Boolean
  val allowsUnencryptedLocalBackup: Boolean
  val enforcesKeystoreKeyProtection: Boolean
}

object DefaultSecurityPolicy : SecurityPolicy {
  override val requiresMandatoryAuthentication: Boolean = true
  override val allowsGuestMode: Boolean = false
  override val allowsUnencryptedLocalBackup: Boolean = false
  override val enforcesKeystoreKeyProtection: Boolean = true
}
