package com.example.core.database

/**
 * Database architecture contract for Phase 2 Core Finance.
 * Specifies SQLCipher-encrypted Room persistence with database-level transactions for transfers.
 */
object DatabaseContract {
  const val DATABASE_NAME: String = "skyplus_money_encrypted.db"
  const val SCHEMA_VERSION_TARGET: Int = 11
  const val ENCRYPTION_ENGINE: String = "Room + SQLCipher (Android Keystore Passphrase)"
  const val STATUS: String = "Phase 6 Active — SQLCipher Encrypted Room Database"
}
