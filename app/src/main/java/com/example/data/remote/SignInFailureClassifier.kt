package com.example.data.remote

/**
 * Why a Google Sign-In attempt did not complete.
 *
 * Kept separate from the UI so the decision is unit testable and so the app stops
 * reporting every `GetCredentialCancellationException` as a genuine user cancel:
 * Credential Manager also raises that exception when Google Play services rejects
 * the request, with the real status code only present inside the message, as in
 * "[16] Account reauth failed.".
 */
enum class SignInFailureKind {
  /** The person dismissed the account sheet, and nothing suggests a provider fault. */
  USER_CANCELLED,

  /** No Google account is available to Credential Manager on this device. */
  NO_ACCOUNT_AVAILABLE,

  /** Provider, Play services or OAuth client configuration problem. */
  PROVIDER_CONFIGURATION,

  /** Credential Manager is not supported on this device. */
  UNSUPPORTED,

  /** Network or DNS failure while contacting Google. */
  NETWORK,

  /** Anything not mapped above. */
  UNKNOWN,
}

/**
 * Non-sensitive view of a failure.
 *
 * It deliberately has **no message field**. The raw exception message is read once,
 * turned into the two booleans below, and then discarded, so it can never be
 * retained, logged or copied anywhere — structurally, not by discipline.
 */
data class SignInFailureSnapshot(
  val exceptionClassName: String,
  val credentialType: String? = null,
  val statusCode: Int? = null,
  val hasProviderReauthSignal: Boolean = false,
  val hasNetworkSignal: Boolean = false,
  val causeChain: List<String> = emptyList(),
)

object SignInFailureClassifier {

  // Google Play services CommonStatusCodes and GoogleSignInStatusCodes values.
  private const val STATUS_NETWORK_ERROR = 7
  private const val STATUS_INTERNAL_ERROR = 8
  private const val STATUS_DEVELOPER_ERROR = 10
  private const val STATUS_CANCELED = 16
  private const val STATUS_API_NOT_CONNECTED = 17
  private const val STATUS_SIGN_IN_FAILED = 12500
  private const val STATUS_SIGN_IN_CANCELLED = 12501
  private const val STATUS_SIGN_IN_IN_PROGRESS = 12502

  /**
   * Status codes that always mean the app's Google configuration is wrong.
   * Status 16 is intentionally absent: on its own it is CommonStatusCodes.CANCELED,
   * which can be a real dismissal. It is only treated as a provider problem when the
   * verified "Account reauth failed" wording accompanies it.
   */
  private val providerStatusCodes =
    setOf(
      STATUS_DEVELOPER_ERROR,
      STATUS_INTERNAL_ERROR,
      STATUS_API_NOT_CONNECTED,
      STATUS_SIGN_IN_FAILED,
      STATUS_SIGN_IN_IN_PROGRESS,
    )

  /**
   * The one verified Google Play services phrase that means an OAuth/signing
   * rejection: the complete "Account reauth failed" response. A bare "reauth failed"
   * or any other wording must NOT raise a provider classification.
   */
  private const val REAUTH_FAILED_SIGNAL = "account reauth failed"

  fun classify(snapshot: SignInFailureSnapshot): SignInFailureKind {
    val statusCode = snapshot.statusCode
    val type = snapshot.credentialType.orEmpty().lowercase()
    val className = snapshot.exceptionClassName.lowercase()

    // 1. An explicit status code wins over the exception type.
    when (statusCode) {
      STATUS_NETWORK_ERROR -> return SignInFailureKind.NETWORK
      STATUS_SIGN_IN_CANCELLED -> return SignInFailureKind.USER_CANCELLED
      STATUS_CANCELED ->
        return if (snapshot.hasProviderReauthSignal) {
          SignInFailureKind.PROVIDER_CONFIGURATION
        } else {
          SignInFailureKind.USER_CANCELLED
        }
      in providerStatusCodes -> return SignInFailureKind.PROVIDER_CONFIGURATION
      else -> Unit
    }

    // 2. The verified provider wording, even without a parsed code.
    if (snapshot.hasProviderReauthSignal) {
      return SignInFailureKind.PROVIDER_CONFIGURATION
    }

    // 3. Network wording.
    if (snapshot.hasNetworkSignal) {
      return SignInFailureKind.NETWORK
    }

    // 4. No account on the device.
    if ("nocredential" in className || "no_credential" in type) {
      return SignInFailureKind.NO_ACCOUNT_AVAILABLE
    }

    // 5. Credential Manager unsupported on this device.
    if ("unsupported" in className || "unsupported" in type) {
      return SignInFailureKind.UNSUPPORTED
    }

    // 6. Provider configuration exception.
    if ("providerconfiguration" in className || "provider_configuration" in type) {
      return SignInFailureKind.PROVIDER_CONFIGURATION
    }

    // 7. A plain cancellation with nothing else attached is a real user cancel.
    if ("cancellation" in className || "cancellation" in type) {
      return SignInFailureKind.USER_CANCELLED
    }

    return SignInFailureKind.UNKNOWN
  }

  /**
   * Reads a Google status code out of a message such as "[16] Account reauth failed."
   * Returns null when the message carries no code.
   */
  fun statusCodeFromMessage(message: String?): Int? {
    if (message.isNullOrBlank()) return null
    val match = Regex("^\\s*\\[(\\d{1,6})]").find(message) ?: return null
    return match.groupValues.getOrNull(1)?.toIntOrNull()
  }

  /**
   * True only for the verified Google Play services "Account reauth failed" response.
   * "Other reauth failed", a bare "reauth failed", "cancelled by user" and
   * "The flow has been cancelled." all return false.
   */
  fun hasReauthFailedSignal(message: String?): Boolean =
    message?.lowercase()?.contains(REAUTH_FAILED_SIGNAL) == true

  /** True when the message describes a connectivity problem. */
  fun hasNetworkSignal(message: String?): Boolean {
    val lower = message?.lowercase() ?: return false
    return "network" in lower || "timeout" in lower || "unable to resolve" in lower
  }

  /**
   * Builds a snapshot from a throwable.
   *
   * The raw message is read here, reduced to a status code and two booleans, and
   * never stored. No token, account identity or payload text can leave this method.
   */
  fun snapshotFrom(throwable: Throwable): SignInFailureSnapshot {
    val causes = mutableListOf<String>()
    var current: Throwable? = throwable.cause
    var depth = 0
    while (current != null && depth < 5) {
      causes += current.javaClass.simpleName
      current = current.cause
      depth++
    }

    val credentialType =
      (throwable as? androidx.credentials.exceptions.GetCredentialException)?.type?.toString()

    val rawMessage = throwable.message

    return SignInFailureSnapshot(
      exceptionClassName = throwable.javaClass.name,
      credentialType = credentialType,
      statusCode = statusCodeFromMessage(rawMessage) ?: statusCodeFromCauseChain(throwable),
      hasProviderReauthSignal = hasReauthFailedSignal(rawMessage),
      hasNetworkSignal = hasNetworkSignal(rawMessage),
      causeChain = causes,
    )
  }

  private fun statusCodeFromCauseChain(throwable: Throwable): Int? {
    var current: Throwable? = throwable
    var depth = 0
    while (current != null && depth < 5) {
      val code =
        try {
          val clazz = current.javaClass
          if (clazz.name.contains("ApiException")) {
            clazz.methods
              .firstOrNull { it.name == "getStatusCode" && it.parameterCount == 0 }
              ?.invoke(current) as? Int
          } else {
            null
          }
        } catch (_: Throwable) {
          null
        }
      if (code != null) return code
      current = current.cause
      depth++
    }
    return null
  }
}
