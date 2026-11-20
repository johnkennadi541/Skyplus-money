package com.example.domain.model

enum class AuthProviderType {
  GOOGLE,
  FACEBOOK_PLANNED,
}

data class AuthenticatedUser(
  val uid: String,
  val displayName: String?,
  val email: String?,
  val photoUrl: String?,
  val isEmailVerified: Boolean,
  val providers: Set<AuthProviderType>,
)

sealed interface AuthSessionState {
  data object Loading : AuthSessionState

  data object Unauthenticated : AuthSessionState

  data class Authenticated(val user: AuthenticatedUser) : AuthSessionState
}

sealed interface AuthError {
  data object NoGoogleAccount : AuthError

  data object CredentialRetrievalFailed : AuthError

  data object MissingIdToken : AuthError

  data object NetworkError : AuthError

  data object SessionExpiredOrInvalid : AuthError

  data object MissingClientConfiguration : AuthError

  data object FirebaseAuthenticationFailed : AuthError

  data object AccountLinkingRequiresOwnershipVerification : AuthError

  /**
   * Google rejected the request for a configuration reason (unregistered signing
   * certificate, disabled provider, stale Play services) rather than a user cancel.
   */
  data object ProviderConfigurationError : AuthError
}

sealed interface AuthResult<out T> {
  data class Success<T>(val data: T) : AuthResult<T>

  data class Failure(val error: AuthError) : AuthResult<Nothing>
}
