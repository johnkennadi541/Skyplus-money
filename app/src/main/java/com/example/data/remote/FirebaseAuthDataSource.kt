package com.example.data.remote

import android.util.Log
import com.example.domain.model.AuthError
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface FirebaseAuthGateway {
  fun observeSession(): Flow<AuthSessionState>

  suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser>

  suspend fun reloadCurrentUser(): AuthResult<AuthenticatedUser?>

  suspend fun signOut()
}

/**
 * Firebase Authentication data source acting as the authoritative session source.
 * Never stores passwords or Google ID tokens and never logs credential values.
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(private val firebaseAuth: FirebaseAuth) :
  FirebaseAuthGateway {

  override fun observeSession(): Flow<AuthSessionState> = callbackFlow {
    val emitCurrent: (FirebaseUser?) -> Unit = { user ->
      if (user == null) {
        trySend(AuthSessionState.Unauthenticated)
      } else {
        trySend(AuthSessionState.Authenticated(user.toDomainUser()))
      }
    }

    emitCurrent(firebaseAuth.currentUser)

    val listener = FirebaseAuth.AuthStateListener { auth -> emitCurrent(auth.currentUser) }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }

  override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> {
    if (idToken.isBlank()) {
      return AuthResult.Failure(AuthError.MissingIdToken)
    }

    return try {
      val credential = GoogleAuthProvider.getCredential(idToken, null)
      val authResult = firebaseAuth.signInWithCredential(credential).await()
      val user = authResult.user
      if (user != null) {
        AuthResult.Success(user.toDomainUser())
      } else {
        AuthResult.Failure(AuthError.FirebaseAuthenticationFailed)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AuthResult.Failure(mapFirebaseException(e))
    }
  }

  override suspend fun reloadCurrentUser(): AuthResult<AuthenticatedUser?> {
    val user = firebaseAuth.currentUser ?: return AuthResult.Success(null)
    return try {
      user.reload().await()
      val refreshed = firebaseAuth.currentUser
      AuthResult.Success(refreshed?.toDomainUser())
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseAuthInvalidUserException) {
      Log.w(TAG, "Cached Firebase session is invalid or disabled (${e.errorCode})")
      firebaseAuth.signOut()
      AuthResult.Failure(AuthError.SessionExpiredOrInvalid)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
      Log.w(TAG, "Cached Firebase credentials expired (${e.errorCode})")
      firebaseAuth.signOut()
      AuthResult.Failure(AuthError.SessionExpiredOrInvalid)
    } catch (e: Exception) {
      AuthResult.Failure(mapFirebaseException(e))
    }
  }

  override suspend fun signOut() {
    firebaseAuth.signOut()
  }

  private fun mapFirebaseException(error: Exception): AuthError {
    return when (error) {
      is FirebaseNetworkException -> {
        Log.w(TAG, "Firebase auth network error")
        AuthError.NetworkError
      }
      is FirebaseAuthUserCollisionException -> {
        Log.w(TAG, "Firebase auth account collision (${error.errorCode}); verified linking required")
        AuthError.AccountLinkingRequiresOwnershipVerification
      }
      is FirebaseAuthInvalidUserException -> {
        Log.w(TAG, "Firebase auth invalid/disabled user (${error.errorCode})")
        AuthError.SessionExpiredOrInvalid
      }
      is FirebaseAuthInvalidCredentialsException -> {
        Log.w(TAG, "Firebase auth rejected credentials (${error.errorCode})")
        AuthError.SessionExpiredOrInvalid
      }
      else -> {
        Log.e(TAG, "Firebase authentication failed: ${error::class.simpleName}")
        AuthError.FirebaseAuthenticationFailed
      }
    }
  }

  private fun FirebaseUser.toDomainUser(): AuthenticatedUser {
    val mappedProviders =
      providerData
        .mapNotNull { info ->
          when (info.providerId) {
            GoogleAuthProvider.PROVIDER_ID -> AuthProviderType.GOOGLE
            else -> null
          }
        }
        .toSet()

    return AuthenticatedUser(
      uid = uid,
      displayName = displayName,
      email = email,
      photoUrl = photoUrl?.toString(),
      isEmailVerified = isEmailVerified,
      providers = mappedProviders,
    )
  }

  private companion object {
    private const val TAG = "SkyplusFirebaseAuth"
  }
}
