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
      smartDetectionRepository?.clearAutoSavedNotice()
      googleCredentialClient.clearCredentialState()
      firebaseAuthGateway.signOut()
    }
    return AuthResult.Success(Unit)
  }
}
