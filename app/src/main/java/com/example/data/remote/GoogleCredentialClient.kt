package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.AuthError
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface GoogleCredentialResult {
  data class Success(val idToken: String) : GoogleCredentialResult

  data object Cancelled : GoogleCredentialResult

  data class Failure(val error: AuthError) : GoogleCredentialResult
}

interface GoogleCredentialClient {
  suspend fun requestGoogleIdToken(context: Context): GoogleCredentialResult

  suspend fun clearCredentialState()
}

@Singleton
class CredentialManagerGoogleClient
@Inject
constructor(private val credentialManager: CredentialManager) : GoogleCredentialClient {

  override suspend fun requestGoogleIdToken(context: Context): GoogleCredentialResult {
    val serverClientId =
      try {
        context.getString(R.string.default_web_client_id).trim()
      } catch (_: Exception) {
        Log.e(TAG, "Missing generated default_web_client_id resource")
        return GoogleCredentialResult.Failure(AuthError.MissingClientConfiguration)
      }

    if (serverClientId.isEmpty()) {
      Log.e(TAG, "Empty default_web_client_id resource")
      return GoogleCredentialResult.Failure(AuthError.MissingClientConfiguration)
    }

    val hostContext = context.findActivity() ?: context
    val webClientIdConfigured = serverClientId.isNotEmpty()
    val googleSignInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleSignInOption).build()

    return try {
      val response = credentialManager.getCredential(request = request, context = hostContext)
      val credential = response.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val parsed = GoogleIdTokenCredential.createFrom(credential.data)
        if (parsed.idToken.isBlank()) {
          GoogleCredentialResult.Failure(AuthError.MissingIdToken)
        } else {
          GoogleCredentialResult.Success(parsed.idToken)
        }
      } else {
        Log.w(TAG, "Unexpected credential type returned by Credential Manager")
        GoogleCredentialResult.Failure(AuthError.MissingIdToken)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialCancellationException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.type}")
      GoogleCredentialResult.Cancelled
    } catch (e: NoCredentialException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.w(TAG, "No Google account credential available on device")
      GoogleCredentialResult.Failure(AuthError.NoGoogleAccount)
    } catch (e: GetCredentialProviderConfigurationException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.e(TAG, "Google credential provider configuration error")
      GoogleCredentialResult.Failure(AuthError.CredentialRetrievalFailed)
    } catch (e: GetCredentialUnsupportedException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.e(TAG, "Credential Manager unsupported on this device")
      GoogleCredentialResult.Failure(AuthError.CredentialRetrievalFailed)
    } catch (e: GoogleIdTokenParsingException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.e(TAG, "Failed to parse Google ID token payload")
      GoogleCredentialResult.Failure(AuthError.MissingIdToken)
    } catch (e: GetCredentialException) {
      recordFailure(e, hostContext, webClientIdConfigured)
      val message = e.message.orEmpty().lowercase()
      if ("network" in message || "timeout" in message || "unable to resolve host" in message) {
        Log.w(TAG, "Credential retrieval failed due to network error")
        GoogleCredentialResult.Failure(AuthError.NetworkError)
      } else {
        Log.e(TAG, "Credential retrieval failed: ${e.type}")
        GoogleCredentialResult.Failure(AuthError.CredentialRetrievalFailed)
      }
    } catch (e: Exception) {
      recordFailure(e, hostContext, webClientIdConfigured)
      Log.e(TAG, "Unexpected credential retrieval failure")
      GoogleCredentialResult.Failure(AuthError.CredentialRetrievalFailed)
    }
  }

  override suspend fun clearCredentialState() {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(TAG, "Unable to clear Credential Manager state: ${e::class.simpleName}")
      }
    }
  }

  /**
   * DEBUG-only: captures the non-sensitive diagnostic detail of a failed attempt.
   * Release builds never collect or display it.
   */
  private fun recordFailure(error: Throwable, context: Context, webClientIdConfigured: Boolean) {
    if (!BuildConfig.DEBUG) return
    AuthDiagnosticsRecorder.recordCredentialFailure(
      stage = AuthDiagnosticStage.CREDENTIAL_REQUEST,
      throwable = error,
      context = context,
      webClientIdConfigured = webClientIdConfigured,
    )
  }

  private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }

  private companion object {
    private const val TAG = "SkyplusGoogleCredential"
  }
}
