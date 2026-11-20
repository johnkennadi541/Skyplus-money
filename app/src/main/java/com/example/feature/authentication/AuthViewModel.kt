package com.example.feature.authentication

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.remote.GoogleCredentialClient
import com.example.data.remote.GoogleCredentialResult
import com.example.domain.model.AuthError
import com.example.domain.model.AuthResult
import com.example.domain.model.AuthSessionState
import com.example.domain.model.ConsentRecord
import com.example.domain.repository.SmartDetectionRepository
import com.example.domain.usecase.CheckUserConsentUseCase
import com.example.domain.usecase.ObserveAuthSessionUseCase
import com.example.domain.usecase.RefreshAuthSessionUseCase
import com.example.domain.usecase.SaveUserConsentUseCase
import com.example.domain.usecase.SignInWithGoogleIdTokenUseCase
import com.example.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
  val sessionState: AuthSessionState = AuthSessionState.Loading,
  val isSigningIn: Boolean = false,
  @StringRes val feedbackMessageRes: Int? = null,
  val isCancellationNotice: Boolean = false,
  val isConsentLoading: Boolean = false,
  val requiresConsent: Boolean = false,
  val consentRecord: ConsentRecord? = null,
  @StringRes val consentErrorMessageRes: Int? = null,
  val isSavingConsent: Boolean = false,
)

@HiltViewModel
class AuthViewModel
@Inject
constructor(
  private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
  private val signInWithGoogleIdTokenUseCase: SignInWithGoogleIdTokenUseCase,
  private val refreshAuthSessionUseCase: RefreshAuthSessionUseCase,
  private val signOutUseCase: SignOutUseCase,
  private val googleCredentialClient: GoogleCredentialClient,
  private val checkUserConsentUseCase: CheckUserConsentUseCase,
  private val saveUserConsentUseCase: SaveUserConsentUseCase,
  private val smartDetectionRepository: SmartDetectionRepository?,
) : ViewModel() {

  constructor(
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    signInWithGoogleIdTokenUseCase: SignInWithGoogleIdTokenUseCase,
    refreshAuthSessionUseCase: RefreshAuthSessionUseCase,
    signOutUseCase: SignOutUseCase,
    googleCredentialClient: GoogleCredentialClient,
    checkUserConsentUseCase: CheckUserConsentUseCase,
    saveUserConsentUseCase: SaveUserConsentUseCase,
  ) : this(
    observeAuthSessionUseCase = observeAuthSessionUseCase,
    signInWithGoogleIdTokenUseCase = signInWithGoogleIdTokenUseCase,
    refreshAuthSessionUseCase = refreshAuthSessionUseCase,
    signOutUseCase = signOutUseCase,
    googleCredentialClient = googleCredentialClient,
    checkUserConsentUseCase = checkUserConsentUseCase,
    saveUserConsentUseCase = saveUserConsentUseCase,
    smartDetectionRepository = null,
  )

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private var lastAuthenticatedUid: String? = null

  init {
    viewModelScope.launch {
      observeAuthSessionUseCase().collect { session ->
        when (session) {
          is AuthSessionState.Authenticated -> {
            if (lastAuthenticatedUid != null && lastAuthenticatedUid != session.user.uid) {
              smartDetectionRepository?.clearAutoSavedNotice()
            }
            lastAuthenticatedUid = session.user.uid
            _uiState.update { current ->
              current.copy(
                sessionState = session,
                isSigningIn = false,
                feedbackMessageRes = null,
                isCancellationNotice = false,
                isConsentLoading = true,
                consentErrorMessageRes = null,
              )
            }
            verifyConsentFailClosed(session.user.uid)
          }
          AuthSessionState.Unauthenticated -> {
            lastAuthenticatedUid = null
            smartDetectionRepository?.clearAutoSavedNotice()
            _uiState.update { current ->
              current.copy(
                sessionState = session,
                isSigningIn = false,
                isConsentLoading = false,
                requiresConsent = false,
                consentRecord = null,
                consentErrorMessageRes = null,
                isSavingConsent = false,
              )
            }
          }
          AuthSessionState.Loading -> {
            _uiState.update { current -> current.copy(sessionState = session) }
          }
        }
      }
    }

    viewModelScope.launch {
      when (val result = refreshAuthSessionUseCase()) {
        is AuthResult.Failure -> {
          if (result.error == AuthError.SessionExpiredOrInvalid) {
            _uiState.update {
              it.copy(
                feedbackMessageRes = mapAuthErrorToStringRes(result.error),
                isCancellationNotice = false,
              )
            }
          }
        }
        is AuthResult.Success -> Unit
      }
    }
  }

  private suspend fun verifyConsentFailClosed(uid: String) {
    val result = runCatching { checkUserConsentUseCase(uid) }
    result
      .onSuccess { record ->
        val needsConsent = record == null || !record.isUpToDate
        _uiState.update { current ->
          current.copy(
            isConsentLoading = false,
            requiresConsent = needsConsent,
            consentRecord = record,
            consentErrorMessageRes = null,
          )
        }
      }
      .onFailure {
        // Fail closed: block workspace access if consent DataStore read fails
        _uiState.update { current ->
          current.copy(
            isConsentLoading = false,
            requiresConsent = true,
            consentRecord = null,
            consentErrorMessageRes = R.string.legal_consent_load_error,
          )
        }
      }
  }

  fun onContinueWithGoogle(activityContext: Context) {
    if (_uiState.value.isSigningIn) return

    _uiState.update {
      it.copy(isSigningIn = true, feedbackMessageRes = null, isCancellationNotice = false)
    }

    viewModelScope.launch {
      when (val credentialResult = googleCredentialClient.requestGoogleIdToken(activityContext)) {
        is GoogleCredentialResult.Success -> {
          when (val authResult = signInWithGoogleIdTokenUseCase(credentialResult.idToken)) {
            is AuthResult.Success -> {
              val user = authResult.data
              if (lastAuthenticatedUid != null && lastAuthenticatedUid != user.uid) {
                smartDetectionRepository?.clearAutoSavedNotice()
              }
              lastAuthenticatedUid = user.uid
              _uiState.update {
                it.copy(
                  sessionState = AuthSessionState.Authenticated(user),
                  isSigningIn = false,
                  feedbackMessageRes = null,
                  isCancellationNotice = false,
                  isConsentLoading = true,
                  consentErrorMessageRes = null,
                )
              }
              verifyConsentFailClosed(user.uid)
            }
            is AuthResult.Failure -> {
              _uiState.update {
                it.copy(
                  isSigningIn = false,
                  feedbackMessageRes = mapAuthErrorToStringRes(authResult.error),
                  isCancellationNotice = false,
                )
              }
            }
          }
        }
        GoogleCredentialResult.Cancelled -> {
          _uiState.update {
            it.copy(
              isSigningIn = false,
              feedbackMessageRes = R.string.auth_cancelled_message,
              isCancellationNotice = true,
            )
          }
        }
        is GoogleCredentialResult.Failure -> {
          _uiState.update {
            it.copy(
              isSigningIn = false,
              feedbackMessageRes = mapAuthErrorToStringRes(credentialResult.error),
              isCancellationNotice = false,
            )
          }
        }
      }
    }
  }

  fun onRetryConsentCheck() {
    val session = _uiState.value.sessionState
    if (session is AuthSessionState.Authenticated) {
      viewModelScope.launch {
        _uiState.update { it.copy(isConsentLoading = true, consentErrorMessageRes = null) }
        verifyConsentFailClosed(session.user.uid)
      }
    }
  }

  fun onAcceptConsent() {
    val session = _uiState.value.sessionState
    if (session is AuthSessionState.Authenticated && !_uiState.value.isSavingConsent) {
      viewModelScope.launch {
        _uiState.update { it.copy(isSavingConsent = true, consentErrorMessageRes = null) }
        val saveResult = runCatching {
          saveUserConsentUseCase(
            session.user.uid,
            ConsentRecord.CURRENT_TERMS_VERSION,
            ConsentRecord.CURRENT_PRIVACY_VERSION,
          )
        }
        saveResult
          .onSuccess {
            val updatedRecord =
              ConsentRecord(
                ConsentRecord.CURRENT_TERMS_VERSION,
                ConsentRecord.CURRENT_PRIVACY_VERSION,
                System.currentTimeMillis(),
              )
            _uiState.update {
              it.copy(
                requiresConsent = false,
                consentRecord = updatedRecord,
                isSavingConsent = false,
                consentErrorMessageRes = null,
              )
            }
          }
          .onFailure {
            // Fail closed: keep consent gate active on save error and allow user to retry
            _uiState.update {
              it.copy(
                requiresConsent = true,
                isSavingConsent = false,
                consentErrorMessageRes = R.string.legal_consent_save_error,
              )
            }
          }
      }
    }
  }

  fun onSignOut() {
    viewModelScope.launch {
      smartDetectionRepository?.clearAutoSavedNotice()
      lastAuthenticatedUid = null
      _uiState.update {
        it.copy(
          isSigningIn = false,
          feedbackMessageRes = null,
          requiresConsent = false,
          consentRecord = null,
          isConsentLoading = false,
          consentErrorMessageRes = null,
          isSavingConsent = false,
        )
      }
      signOutUseCase()
    }
  }

  fun onDismissFeedback() {
    _uiState.update { it.copy(feedbackMessageRes = null, isCancellationNotice = false) }
  }

  @StringRes
  private fun mapAuthErrorToStringRes(error: AuthError): Int =
    when (error) {
      AuthError.NoGoogleAccount -> R.string.auth_error_no_google_account
      AuthError.CredentialRetrievalFailed -> R.string.auth_error_credential_retrieval
      AuthError.MissingIdToken -> R.string.auth_error_missing_id_token
      AuthError.NetworkError -> R.string.auth_error_network
      AuthError.SessionExpiredOrInvalid -> R.string.auth_error_session_expired
      AuthError.MissingClientConfiguration -> R.string.auth_error_missing_config
      AuthError.FirebaseAuthenticationFailed -> R.string.auth_error_firebase_failed
      AuthError.AccountLinkingRequiresOwnershipVerification ->
        R.string.auth_error_linking_verification
    }
}
