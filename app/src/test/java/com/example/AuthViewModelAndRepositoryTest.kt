package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.FirebaseAuthGateway
import com.example.data.remote.GoogleCredentialClient
import com.example.data.remote.GoogleCredentialResult
import com.example.data.repository.AuthRepositoryImpl
import com.example.domain.model.AuthError
import com.example.domain.model.AuthProviderType
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.AuthenticatedUser
import com.example.domain.model.ConsentRecord
import com.example.domain.repository.ConsentRepository
import com.example.domain.usecase.CheckUserConsentUseCase
import com.example.domain.usecase.ObserveAuthSessionUseCase
import com.example.domain.usecase.RefreshAuthSessionUseCase
import com.example.domain.usecase.SaveUserConsentUseCase
import com.example.domain.usecase.SignInWithGoogleIdTokenUseCase
import com.example.domain.usecase.SignOutUseCase
import com.example.feature.authentication.AuthViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthViewModelAndRepositoryTest {

  private val testDispatcher = StandardTestDispatcher()

  private class FakeGoogleCredentialClient : GoogleCredentialClient {
    var nextResult: GoogleCredentialResult = GoogleCredentialResult.Cancelled
    var deferredResult: CompletableDeferred<GoogleCredentialResult>? = null
    var requestCalls: Int = 0
    var clearCalls: Int = 0
    var clearFailure: Exception? = null

    override suspend fun requestGoogleIdToken(context: Context): GoogleCredentialResult {
      requestCalls += 1
      deferredResult?.let { return it.await() }
      return nextResult
    }

    override suspend fun clearCredentialState() {
      clearCalls += 1
      clearFailure?.let { throw it }
    }
  }

  private class FakeFirebaseAuthGateway : FirebaseAuthGateway {
    val sessionFlow = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
    var nextSignInResult: AuthResult<AuthenticatedUser>? = null
    var nextReloadResult: AuthResult<AuthenticatedUser?> = AuthResult.Success(null)
    var signInCalls: Int = 0
    var lastToken: String? = null
    var signOutCalls: Int = 0
    var signOutFailure: Exception? = null

    override fun observeSession(): Flow<AuthSessionState> = sessionFlow

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthenticatedUser> {
      signInCalls += 1
      lastToken = idToken
      val result =
        nextSignInResult
          ?: AuthResult.Success(
            AuthenticatedUser(
              uid = "uid_test_123",
              displayName = "Test User",
              email = "test@example.com",
              photoUrl = null,
              isEmailVerified = true,
              providers = setOf(AuthProviderType.GOOGLE),
            )
          )
      if (result is AuthResult.Success) {
        sessionFlow.value = AuthSessionState.Authenticated(result.data)
      }
      return result
    }

    override suspend fun reloadCurrentUser(): AuthResult<AuthenticatedUser?> {
      if (nextReloadResult is AuthResult.Failure &&
        (nextReloadResult as AuthResult.Failure).error == AuthError.SessionExpiredOrInvalid
      ) {
        sessionFlow.value = AuthSessionState.Unauthenticated
      }
      return nextReloadResult
    }

    override suspend fun signOut() {
      signOutCalls += 1
      signOutFailure?.let { throw it }
      sessionFlow.value = AuthSessionState.Unauthenticated
    }
  }

  private class FakeConsentRepository : ConsentRepository {
    val store = mutableMapOf<String, ConsentRecord>()
    override suspend fun getConsent(uid: String): ConsentRecord? = store[uid]
    override suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String) {
      store[uid] = ConsentRecord(termsVersion, privacyVersion, System.currentTimeMillis())
    }
    override suspend fun clearConsent(uid: String) {
      store.remove(uid)
    }
  }

  private lateinit var context: Context
  private lateinit var credentialClient: FakeGoogleCredentialClient
  private lateinit var firebaseGateway: FakeFirebaseAuthGateway
  private lateinit var repository: AuthRepositoryImpl
  private lateinit var consentRepository: FakeConsentRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    credentialClient = FakeGoogleCredentialClient()
    firebaseGateway = FakeFirebaseAuthGateway()
    repository = AuthRepositoryImpl(firebaseGateway, credentialClient)
    consentRepository = FakeConsentRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): AuthViewModel =
    AuthViewModel(
      observeAuthSessionUseCase = ObserveAuthSessionUseCase(repository),
      signInWithGoogleIdTokenUseCase = SignInWithGoogleIdTokenUseCase(repository),
      refreshAuthSessionUseCase = RefreshAuthSessionUseCase(repository),
      signOutUseCase = SignOutUseCase(repository),
      googleCredentialClient = credentialClient,
      checkUserConsentUseCase = CheckUserConsentUseCase(consentRepository),
      saveUserConsentUseCase = SaveUserConsentUseCase(consentRepository),
    )

  @Test
  fun `successful Google credential exchange transitions session to Authenticated`() = runTest {
    credentialClient.nextResult = GoogleCredentialResult.Success("test-google-id-token")
    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)

    viewModel.onContinueWithGoogle(context)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.sessionState is AuthSessionState.Authenticated)
    assertEquals("uid_test_123", (state.sessionState as AuthSessionState.Authenticated).user.uid)
    assertEquals("test-google-id-token", firebaseGateway.lastToken)
    assertFalse(state.isSigningIn)
    assertNull(state.feedbackMessageRes)
  }

  @Test
  fun `missing or blank ID token fails before hitting Firebase gateway`() = runTest {
    val useCase = SignInWithGoogleIdTokenUseCase(repository)
    val blankResult = useCase("   ")

    assertTrue(blankResult is AuthResult.Failure)
    assertEquals(AuthError.MissingIdToken, (blankResult as AuthResult.Failure).error)
    assertEquals(0, firebaseGateway.signInCalls)

    credentialClient.nextResult = GoogleCredentialResult.Failure(AuthError.MissingIdToken)
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.onContinueWithGoogle(context)
    advanceUntilIdle()

    assertEquals(R.string.auth_error_missing_id_token, viewModel.uiState.value.feedbackMessageRes)
    assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)
  }

  @Test
  fun `user cancellation resets loading and allows subsequent retry`() = runTest {
    credentialClient.nextResult = GoogleCredentialResult.Cancelled
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onContinueWithGoogle(context)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSigningIn)
    assertTrue(viewModel.uiState.value.isCancellationNotice)
    assertEquals(R.string.auth_cancelled_message, viewModel.uiState.value.feedbackMessageRes)
    assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)

    // Retry succeeds
    credentialClient.nextResult = GoogleCredentialResult.Success("retry-id-token")
    viewModel.onContinueWithGoogle(context)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.sessionState is AuthSessionState.Authenticated)
    assertNull(viewModel.uiState.value.feedbackMessageRes)
  }

  @Test
  fun `handles no Google account, credential retrieval, network, and Firebase failures cleanly`() =
    runTest {
      val viewModel = createViewModel()
      advanceUntilIdle()

      // No Google account
      credentialClient.nextResult = GoogleCredentialResult.Failure(AuthError.NoGoogleAccount)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()
      assertEquals(R.string.auth_error_no_google_account, viewModel.uiState.value.feedbackMessageRes)

      // Credential retrieval failure
      credentialClient.nextResult =
        GoogleCredentialResult.Failure(AuthError.CredentialRetrievalFailed)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()
      assertEquals(
        R.string.auth_error_credential_retrieval,
        viewModel.uiState.value.feedbackMessageRes,
      )

      // Network failure during credential retrieval
      credentialClient.nextResult = GoogleCredentialResult.Failure(AuthError.NetworkError)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()
      assertEquals(R.string.auth_error_network, viewModel.uiState.value.feedbackMessageRes)

      // Firebase auth rejection & account collision requiring verified ownership
      credentialClient.nextResult = GoogleCredentialResult.Success("valid-token")
      firebaseGateway.nextSignInResult =
        AuthResult.Failure(AuthError.FirebaseAuthenticationFailed)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()
      assertEquals(R.string.auth_error_firebase_failed, viewModel.uiState.value.feedbackMessageRes)

      firebaseGateway.nextSignInResult =
        AuthResult.Failure(AuthError.AccountLinkingRequiresOwnershipVerification)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()
      assertEquals(
        R.string.auth_error_linking_verification,
        viewModel.uiState.value.feedbackMessageRes,
      )
      assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)
    }

  @Test
  fun `expired session reload clears authentication and surfaces session expired message`() =
    runTest {
      firebaseGateway.nextReloadResult = AuthResult.Failure(AuthError.SessionExpiredOrInvalid)
      val viewModel = createViewModel()
      advanceUntilIdle()

      assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)
      assertEquals(R.string.auth_error_session_expired, viewModel.uiState.value.feedbackMessageRes)
    }

  @Test
  fun `Firebase sign out is attempted even when Google credential cleanup fails`() = runTest {
    credentialClient.clearFailure = IllegalStateException("Credential Manager unavailable")

    val result = repository.signOut()

    assertTrue(result is AuthResult.Success)
    assertEquals(1, firebaseGateway.signOutCalls)
    assertEquals(1, credentialClient.clearCalls)
    assertEquals(AuthSessionState.Unauthenticated, firebaseGateway.sessionFlow.value)
  }

  @Test
  fun `Google credential cleanup is attempted even when Firebase sign out fails`() = runTest {
    firebaseGateway.signOutFailure = IllegalStateException("Firebase sign out failed")

    val result = runCatching { repository.signOut() }

    assertTrue(result.isFailure)
    assertEquals(1, firebaseGateway.signOutCalls)
    assertEquals(1, credentialClient.clearCalls)
  }

  @Test
  fun `repeated login taps while in flight are ignored and signOut clears credential state`() =
    runTest {
      val deferred = CompletableDeferred<GoogleCredentialResult>()
      credentialClient.deferredResult = deferred
      val viewModel = createViewModel()
      advanceUntilIdle()

      viewModel.onContinueWithGoogle(context)
      viewModel.onContinueWithGoogle(context)
      advanceUntilIdle()

      assertTrue(viewModel.uiState.value.isSigningIn)
      assertEquals(1, credentialClient.requestCalls)

      deferred.complete(GoogleCredentialResult.Success("final-token"))
      advanceUntilIdle()
      assertTrue(viewModel.uiState.value.sessionState is AuthSessionState.Authenticated)

      viewModel.onSignOut()
      advanceUntilIdle()

      assertEquals(1, credentialClient.clearCalls)
      assertEquals(1, firebaseGateway.signOutCalls)
      assertEquals(AuthSessionState.Unauthenticated, viewModel.uiState.value.sessionState)
    }
}
