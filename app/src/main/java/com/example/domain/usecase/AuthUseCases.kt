package com.example.domain.usecase

import com.example.domain.model.AuthError
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAuthSessionUseCase @Inject constructor(private val authRepository: AuthRepository) {
  operator fun invoke(): Flow<AuthSessionState> = authRepository.observeAuthSession()
}

class SignInWithGoogleIdTokenUseCase
@Inject
constructor(private val authRepository: AuthRepository) {
  suspend operator fun invoke(idToken: String): AuthResult<AuthenticatedUser> {
    if (idToken.isBlank()) {
      return AuthResult.Failure(AuthError.MissingIdToken)
    }
    return authRepository.signInWithGoogleIdToken(idToken)
  }
}

class RefreshAuthSessionUseCase @Inject constructor(private val authRepository: AuthRepository) {
  suspend operator fun invoke(): AuthResult<AuthenticatedUser?> =
    authRepository.refreshAndValidateSession()
}

class SignOutUseCase @Inject constructor(private val authRepository: AuthRepository) {
  suspend operator fun invoke(): AuthResult<Unit> = authRepository.signOut()
}
