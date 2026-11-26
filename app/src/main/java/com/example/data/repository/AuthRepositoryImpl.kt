package com.example.data.repository

import com.example.data.remote.FirebaseAuthGateway
import com.example.data.remote.GoogleCredentialClient
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.SmartDetectionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl
@Inject
constructor(
  private val firebaseAuthGateway: FirebaseAuthGateway,
  private val googleCredentialClient: GoogleCredentialClient,
  private val smartDetectionRepository: SmartDetectionRepository?,
) : AuthRepository {

  constructor(
    firebaseAuthGateway: FirebaseAuthGateway,
    googleCredentialClient: GoogleCredentialClient,
  ) : this(firebaseAuthGateway, googleCredentialClient, null)

  override fun observeAuthSession(): Flow<AuthSessionState> = firebaseAuthGateway.observeSession()

  override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> =
    firebaseAuthGateway.signInWithGoogleIdToken(idToken)

  override suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?> =
    firebaseAuthGateway.reloadCurrentUser()

  override suspend fun signOut(): AuthResult<Unit> {
    withContext(NonCancellable) {
      // Firebase is the authoritative session. Attempt it first so a credential-cleanup
      // failure can never leave the Firebase user signed in.
      var firebaseSignOutFailure: Exception? = null
      try {
        firebaseAuthGateway.signOut()
      } catch (error: Exception) {
        firebaseSignOutFailure = error
      }

      // These are best-effort local cleanups. They must not prevent the Firebase sign-out
      // attempt, and a failed cleanup must not turn a completed Firebase sign-out into failure.
      try {
        googleCredentialClient.clearCredentialState()
      } catch (_: Exception) {
        // Credential Manager cleanup can be retried by the platform on the next sign-in.
      }
      try {
        smartDetectionRepository?.clearAutoSavedNotice()
      } catch (_: Exception) {
        // Clearing an in-memory notice is best-effort; session ownership is still enforced.
      }

      firebaseSignOutFailure?.let { throw it }
    }
    return AuthResult.Success(Unit)
  }
}
