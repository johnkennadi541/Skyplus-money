package com.example.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PassphraseFailureReason {
  KEYSTORE_ENCRYPT_FAILED,
  KEYSTORE_DECRYPT_FAILED,
  KEY_CORRUPTED,
  CORRUPTED_VAULT_METADATA,
  MISSING_KEY_FOR_EXISTING_DATABASE,
  PERSISTENCE_FAILED,
  PERSISTENCE_VERIFICATION_FAILED,
  SQLCIPHER_INIT_FAILED,
}

class DatabaseSecurityException(
  val reason: PassphraseFailureReason,
  val isRecoverable: Boolean,
  override val message: String,
  override val cause: Throwable? = null,
) : IllegalStateException(message, cause)

@Singleton
class DatabaseSecurityMonitor @Inject constructor() {
  private val _securityIssue = MutableStateFlow<DatabaseSecurityException?>(null)
  val securityIssue: StateFlow<DatabaseSecurityException?> = _securityIssue.asStateFlow()

  private var retryAction: (() -> Boolean)? = null

  fun reportIssue(issue: DatabaseSecurityException) {
    _securityIssue.value = issue
  }

  fun clearIssue() {
    _securityIssue.value = null
  }

  internal fun registerRetryHandler(handler: () -> Boolean) {
    retryAction = handler
  }

  fun retryInitialization(): Boolean {
    val handler = retryAction ?: return false
    return handler.invoke()
  }
}
