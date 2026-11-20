package com.example.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.firebase.FirebaseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEBUG-ONLY Google Sign-In diagnostics for SKYPLUS MONEY.
 *
 * Purpose: distinguish a genuine user cancellation from a configuration or developer error
 * (unregistered signing certificate, disabled Google provider, outdated Play Services, ...).
 *
 * Safety rules enforced here:
 * - Never records ID tokens, access tokens, authorization codes, passwords, emails or OTPs.
 * - Every captured message passes through [AuthDiagnosticSanitizer] before it is stored.
 * - Nothing in this file is displayed or collected in release builds; callers gate on
 *   `BuildConfig.DEBUG` and the UI is rendered only in debug builds.
 */
enum class AuthDiagnosticStage {
  CREDENTIAL_REQUEST,
  FIREBASE_SIGN_IN,
}

enum class AuthDiagnosticOutcome {
  /** The Google sheet was dismissed without an error code (most likely a real user cancel). */
  CANCELLED_BY_USER,

  /** A cancellation exception that also carried a provider/developer status code. */
  CANCELLED_WITH_CONFIG_SUSPECT,

  /** No Google account is available to Credential Manager on this device. */
  NO_CREDENTIAL_AVAILABLE,

  /** Provider/Play Services/OAuth client configuration problem (not the user's fault). */
  PROVIDER_CONFIGURATION_ERROR,

  /** ApiException status 10: package name or signing SHA-1 not registered for this client. */
  DEVELOPER_ERROR,

  NETWORK_ERROR,

  /** The ID token was obtained but Firebase Authentication rejected or failed the exchange. */
  FIREBASE_AUTH_ERROR,

  /** An unmapped failure; read the captured message. */
  UNKNOWN_ERROR,
}

/** Sanitised, non-sensitive summary of one failed sign-in attempt. */
data class AuthSignInDiagnostics(
  val stage: AuthDiagnosticStage = AuthDiagnosticStage.CREDENTIAL_REQUEST,
  val outcome: AuthDiagnosticOutcome = AuthDiagnosticOutcome.UNKNOWN_ERROR,
  val exceptionClassName: String? = null,
  val credentialExceptionType: String? = null,
  val statusCode: Int? = null,
  val message: String? = null,
  val causeChain: List<String> = emptyList(),
  val firebaseErrorCode: String? = null,
  val packageName: String? = null,
  val signingSha1: String? = null,
  val signingSha256: String? = null,
  val webClientIdConfigured: Boolean = false,
  val hint: String = "",
) {
  /** Compact single-line form for logcat. Contains no tokens or personal data. */
  fun describe(): String =
    "stage=$stage outcome=$outcome statusCode=${statusCode ?: "-"} " +
      "type=${credentialExceptionType ?: "-"} exception=${exceptionClassName ?: "-"} " +
      "firebaseCode=${firebaseErrorCode ?: "-"} pkg=${packageName ?: "-"} " +
      "sha1=${signingSha1 ?: "-"} webClientId=$webClientIdConfigured msg=${message ?: "-"}"
}

/** Removes anything that could be a credential or personal data from a raw error message. */
object AuthDiagnosticSanitizer {
  private val JWT = Regex("eyJ[A-Za-z0-9_\\-.]{8,}")
  private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
  private val LONG_BLOB = Regex("[A-Za-z0-9_\\-+/=]{40,}")
  private val OAUTH_CLIENT_ID = Regex("[0-9]{6,}-[A-Za-z0-9_\\-]{10,}")
  private const val MAX_LENGTH = 240

  fun sanitize(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var out = raw
    out = JWT.replace(out, "<redacted-token>")
    out = OAUTH_CLIENT_ID.replace(out, "<redacted-client-id>")
    out = EMAIL.replace(out, "<redacted-email>")
    out = LONG_BLOB.replace(out, "<redacted>")
    out = out.replace(Regex("\\s+"), " ").trim()
    return if (out.length > MAX_LENGTH) out.take(MAX_LENGTH) + "…" else out
  }
}

/** Plain, testable snapshot of a failure. No Android runtime required. */
data class AuthFailureSnapshot(
  val exceptionClassName: String,
  val credentialExceptionType: String? = null,
  val statusCode: Int? = null,
  val message: String? = null,
  val causeChain: List<String> = emptyList(),
  val firebaseErrorCode: String? = null,
) {
  companion object {
    private const val MAX_CAUSE_DEPTH = 6

    fun from(throwable: Throwable): AuthFailureSnapshot {
      val causes = mutableListOf<String>()
      var current: Throwable? = throwable.cause
      var depth = 0
      while (current != null && depth < MAX_CAUSE_DEPTH) {
        causes += current.javaClass.simpleName
        current = current.cause
        depth++
      }

      val credentialType =
        (throwable as? androidx.credentials.exceptions.GetCredentialException)?.type?.toString()

      return AuthFailureSnapshot(
        exceptionClassName = throwable.javaClass.name,
        credentialExceptionType = credentialType,
        statusCode = statusCodeOf(throwable),
        message = AuthDiagnosticSanitizer.sanitize(throwable.message),
        causeChain = causes,
        firebaseErrorCode = (throwable as? FirebaseException)?.errorCode,
      )
    }

    /**
     * Walks the cause chain and reads `getStatusCode()` from any `ApiException` it finds.
     * Reflection is used so this file needs no Play Services compile-time dependency.
     */
    private fun statusCodeOf(throwable: Throwable): Int? {
      var current: Throwable? = throwable
      var depth = 0
      while (current != null && depth < MAX_CAUSE_DEPTH) {
        val code = readStatusCode(current)
        if (code != null) return code
        current = current.cause
        depth++
      }
      return null
    }

    private fun readStatusCode(throwable: Throwable): Int? =
      try {
        val clazz = throwable.javaClass
        if (clazz.name.contains("ApiException")) {
          val getter = clazz.methods.firstOrNull { it.name == "getStatusCode" && it.parameterCount == 0 }
          getter?.invoke(throwable) as? Int
        } else {
          null
        }
      } catch (_: Throwable) {
        null
      }
  }
}

/** Maps a failure snapshot to an outcome. Pure logic — unit tested without Android. */
object AuthDiagnosticClassifier {
  private const val STATUS_DEVELOPER_ERROR = 10
  private const val STATUS_NETWORK_ERROR = 7
  private const val STATUS_INTERNAL_ERROR = 8
  private const val STATUS_API_NOT_CONNECTED = 17
  private const val STATUS_SIGN_IN_FAILED = 12500

  fun classify(snapshot: AuthFailureSnapshot): AuthDiagnosticOutcome {
    val name = snapshot.exceptionClassName.lowercase()
    val type = snapshot.credentialExceptionType.orEmpty().lowercase()
    val message = snapshot.message.orEmpty().lowercase()
    val statusCode = snapshot.statusCode
    val isCancellation = "cancellation" in name || "cancellation" in type

    if (isCancellation) {
      return when (statusCode) {
        STATUS_DEVELOPER_ERROR -> AuthDiagnosticOutcome.DEVELOPER_ERROR
        STATUS_SIGN_IN_FAILED,
        STATUS_API_NOT_CONNECTED,
        STATUS_INTERNAL_ERROR -> AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR
        STATUS_NETWORK_ERROR -> AuthDiagnosticOutcome.NETWORK_ERROR
        else ->
          if (hasProviderFailureInCauseChain(snapshot)) {
            AuthDiagnosticOutcome.CANCELLED_WITH_CONFIG_SUSPECT
          } else {
            AuthDiagnosticOutcome.CANCELLED_BY_USER
          }
      }
    }

    if ("nocredential" in name || "no_credential" in type) {
      return AuthDiagnosticOutcome.NO_CREDENTIAL_AVAILABLE
    }

    if ("providerconfiguration" in name ||
      "provider_configuration" in type ||
      "unsupported" in name ||
      "unsupported" in type
    ) {
      return AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR
    }

    if (statusCode == STATUS_DEVELOPER_ERROR) {
      return AuthDiagnosticOutcome.DEVELOPER_ERROR
    }

    if (statusCode == STATUS_SIGN_IN_FAILED || statusCode == STATUS_API_NOT_CONNECTED) {
      return AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR
    }

    if (statusCode == STATUS_NETWORK_ERROR ||
      "network" in message ||
      "timeout" in message ||
      "unable to resolve host" in message ||
      "unable to resolve" in message
    ) {
      return AuthDiagnosticOutcome.NETWORK_ERROR
    }

    return AuthDiagnosticOutcome.UNKNOWN_ERROR
  }

  private fun hasProviderFailureInCauseChain(snapshot: AuthFailureSnapshot): Boolean =
    snapshot.causeChain.any { cause ->
      val lower = cause.lowercase()
      "apiexception" in lower || "googleplayservices" in lower || "gms" in lower
    }

  fun hintFor(outcome: AuthDiagnosticOutcome): String =
    when (outcome) {
      AuthDiagnosticOutcome.CANCELLED_BY_USER ->
        "The Google sheet was dismissed with no error code. If you did not dismiss it, compare the signing SHA-1 shown below with the Android OAuth client in Firebase/Google Cloud."
      AuthDiagnosticOutcome.CANCELLED_WITH_CONFIG_SUSPECT ->
        "Cancellation exception carrying a provider failure. This is not a plain user cancel: check the Google provider, OAuth consent screen and registered SHA-1."
      AuthDiagnosticOutcome.NO_CREDENTIAL_AVAILABLE ->
        "No Google account is available to Credential Manager. Add a Google account in device Settings and make sure Google Play Services is up to date."
      AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR ->
        "Google provider / Play Services / OAuth client configuration problem. Verify Google Sign-In is enabled in Firebase Authentication and that Play Services is current."
      AuthDiagnosticOutcome.DEVELOPER_ERROR ->
        "ApiException status 10. The package name and signing certificate SHA-1 of this APK are not registered for the Android OAuth client of project skyplus-money."
      AuthDiagnosticOutcome.NETWORK_ERROR ->
        "Network or DNS failure while contacting Google. Check connectivity and retry."
      AuthDiagnosticOutcome.FIREBASE_AUTH_ERROR ->
        "The Google ID token was obtained, so signing and package are fine. Firebase Authentication rejected the exchange — read the Firebase error code."
      AuthDiagnosticOutcome.UNKNOWN_ERROR ->
        "Unmapped failure. Read the exception class, status code and message captured below."
    }
}

/** Reads the installed APK's own signing certificates. Never throws. */
object AppSigningFingerprint {
  private var cached: Pair<String?, String?>? = null

  fun read(context: Context): Pair<String?, String?> {
    cached?.let { return it }
    val result =
      try {
        val packageInfo =
          @Suppress("DEPRECATION")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
              context.packageName,
              PackageManager.GET_SIGNING_CERTIFICATES,
            )
          } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
          }

        val signatures =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            when {
              signingInfo == null -> null
              signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
              else -> signingInfo.signingCertificateHistory
            }
          } else {
            @Suppress("DEPRECATION") packageInfo.signatures
          }

        val bytes = signatures?.firstOrNull()?.toByteArray()
        if (bytes == null) {
          null to null
        } else {
          fingerprint(bytes, "SHA-1") to fingerprint(bytes, "SHA-256")
        }
      } catch (_: Throwable) {
        null to null
      }
    cached = result
    return result
  }

  @VisibleForTesting
  fun clearCache() {
    cached = null
  }

  private fun fingerprint(bytes: ByteArray, algorithm: String): String? =
    try {
      val digest = java.security.MessageDigest.getInstance(algorithm).digest(bytes)
      digest.joinToString(":") { byte -> "%02X".format(byte) }
    } catch (_: Throwable) {
      null
    }
}

/**
 * Holds the most recent failure so the debug UI can show it. DEBUG builds only —
 * callers guard every write with `BuildConfig.DEBUG`.
 */
object AuthDiagnosticsRecorder {
  private const val TAG = "SkyplusAuthDiagnostics"

  private val _last = MutableStateFlow<AuthSignInDiagnostics?>(null)
  val last: StateFlow<AuthSignInDiagnostics?> = _last.asStateFlow()

  fun recordCredentialFailure(
    stage: AuthDiagnosticStage,
    throwable: Throwable,
    context: Context?,
    webClientIdConfigured: Boolean = false,
  ) {
    val snapshot = AuthFailureSnapshot.from(throwable)
    val outcome = AuthDiagnosticClassifier.classify(snapshot)
    val signing = context?.let { AppSigningFingerprint.read(it) }
    val record =
      AuthSignInDiagnostics(
        stage = stage,
        outcome = outcome,
        exceptionClassName = snapshot.exceptionClassName,
        credentialExceptionType = snapshot.credentialExceptionType,
        statusCode = snapshot.statusCode,
        message = snapshot.message,
        causeChain = snapshot.causeChain,
        firebaseErrorCode = null,
        packageName = context?.packageName,
        signingSha1 = signing?.first,
        signingSha256 = signing?.second,
        webClientIdConfigured = webClientIdConfigured,
        hint = AuthDiagnosticClassifier.hintFor(outcome),
      )
    _last.value = record
    Log.w(TAG, record.describe())
  }

  fun recordFirebaseFailure(throwable: Throwable) {
    val previous = _last.value
    val snapshot = AuthFailureSnapshot.from(throwable)
    val record =
      (previous ?: AuthSignInDiagnostics()).copy(
        stage = AuthDiagnosticStage.FIREBASE_SIGN_IN,
        outcome = AuthDiagnosticOutcome.FIREBASE_AUTH_ERROR,
        exceptionClassName = snapshot.exceptionClassName,
        message = snapshot.message ?: previous?.message,
        causeChain = snapshot.causeChain,
        firebaseErrorCode = snapshot.firebaseErrorCode,
        hint = AuthDiagnosticClassifier.hintFor(AuthDiagnosticOutcome.FIREBASE_AUTH_ERROR),
      )
    _last.value = record
    Log.w(TAG, record.describe())
  }

  fun clear() {
    _last.value = null
  }

  @VisibleForTesting
  fun reset() {
    _last.value = null
    AppSigningFingerprint.clearCache()
  }
}
