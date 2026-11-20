package com.example.domain.repository

import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import kotlinx.coroutines.flow.Flow

/**
 * Authoritative authentication repository backed by Firebase Authentication.
 * Guest mode and unverified email-only account auto-linking are disallowed.
 */
interface AuthRepository {
  fun observeAuthSession(): Flow<AuthSessionState>

  suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser>

  suspend fun refreshAndValidateSession(): AuthResult<AuthenticatedUser?>

  suspend fun signOut(): AuthResult<Unit>
}
