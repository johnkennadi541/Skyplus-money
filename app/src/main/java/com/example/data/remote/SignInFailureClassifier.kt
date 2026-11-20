package com.example.data.remote

/**
 * Why a Google Sign-In attempt did not complete.
 *
 * Kept separate from the UI so the decision is unit testable and so the app stops
 * reporting every `GetCredentialCancellationException` as a genuine user cancel:
 * Credential Manager also raises that exception when Google Play services rejects
 * the request (for example an unregistered signing certificate), with the real
 * status code only present inside the message, as in "[16] Account reauth failed.".
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

/** Non-sensitive view of a failure. No tokens, no account identity, no payload text. */
data class SignInFailureSnapshot(
  val exceptionClassName: String,
  val credentialType: String? = null,
  val statusCode: Int? = null,
  val message: String? = null,
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

  /** Status codes that mean the app's Google configuration is wrong, not the user. */
  private val providerStatusCodes =
    setOf(
      STATUS_DEVELOPER_ERROR,
      STATUS_INTERNAL_ERROR,
      STATUS_CANCELED,
      STATUS_API_NOT_CONNECTED,
      STATUS_SIGN_IN_FAILED,
      STATUS_SIGN_IN_IN_PROGRESS,
    )

  /** Message fragments Google Play services uses when the OAuth client rejects the app. */
  private val providerMessageFragments =
    listOf(
      "reauth",
      "apiexception",
      "api exception",
      "developer error",
      "not registered",
      "unknown calling package",
      "10:",
      "16:",
    )

  fun classify(snapshot: SignInFailureSnapshot): SignInFailureKind {
    val statusCode = snapshot.statusCode
    val type = snapshot.credentialType.orEmpty().lowercase()
    val className = snapshot.exceptionClassName.lowercase()
    val message = snapshot.message.orEmpty().lowercase()

    // 1. An explicit status code always wins over the exception type.
    when (statusCode) {
      STATUS_NETWORK_ERROR -> return SignInFailureKind.NETWORK
      STATUS_SIGN_IN_CANCELLED -> return SignInFailureKind.USER_CANCELLED
      in providerStatusCodes -> return SignInFailureKind.PROVIDER_CONFIGURATION
      else -> Unit
    }

    // 2. A provider failure described in the message, without a parsed code.
    if (providerMessageFragments.any { it in message }) {
      return SignInFailureKind.PROVIDER_CONFIGURATION
    }

    // 3. No account on the device.
    if ("nocredential" in className || "no_credential" in type) {
      return SignInFailureKind.NO_ACCOUNT_AVAILABLE
    }

    // 4. Credential Manager unsupported on this device.
    if ("unsupported" in className || "unsupported" in type) {
      return SignInFailureKind.UNSUPPORTED
    }

    // 5. Provider configuration exception.
    if ("providerconfiguration" in className || "provider_configuration" in type) {
      return SignInFailureKind.PROVIDER_CONFIGURATION
    }

    // 6. Network wording.
    if ("network" in message || "timeout" in message || "unable to resolve" in message) {
      return SignInFailureKind.NETWORK
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
   * Builds a snapshot from a throwable. Only class names, the Credential Manager
   * type, a parsed status code and a length-capped message are kept — never a token.
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

    val message = throwable.message?.take(200)

    return SignInFailureSnapshot(
      exceptionClassName = throwable.javaClass.name,
      credentialType = credentialType,
      statusCode =
        statusCodeFromMessage(message) ?: statusCodeFromCauseChain(throwable),
      message = message,
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
