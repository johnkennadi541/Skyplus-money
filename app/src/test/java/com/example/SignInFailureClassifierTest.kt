package com.example

import com.example.data.remote.SignInFailureClassifier
import com.example.data.remote.SignInFailureKind
import com.example.data.remote.SignInFailureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The app used to report every GetCredentialCancellationException as a genuine
 * user cancel. These tests pin the corrected behaviour: a provider status code or
 * provider wording wins over the exception type.
 */
class SignInFailureClassifierTest {

  private fun snapshot(
    className: String = "androidx.credentials.exceptions.GetCredentialCancellationException",
    type: String? = "androidx.credentials.type.GetCredentialCancellationException",
    statusCode: Int? = null,
    message: String? = null,
    causeChain: List<String> = emptyList(),
  ) = SignInFailureSnapshot(
    exceptionClassName = className,
    credentialType = type,
    statusCode = statusCode,
    message = message,
    causeChain = causeChain,
  )

  // ---- the real production case --------------------------------------------

  @Test
  fun `status 16 with account reauth message is a provider problem, not a user cancel`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(message = "[16] Account reauth failed.")
          .copy(statusCode = SignInFailureClassifier.statusCodeFromMessage("[16] Account reauth failed."))
      )

    assertEquals(SignInFailureKind.PROVIDER_CONFIGURATION, outcome)
  }

  @Test
  fun `status code is parsed out of the leading bracket group`() {
    assertEquals(16, SignInFailureClassifier.statusCodeFromMessage("[16] Account reauth failed."))
    assertEquals(10, SignInFailureClassifier.statusCodeFromMessage("[10] Developer error"))
    assertEquals(12500, SignInFailureClassifier.statusCodeFromMessage("  [12500] Sign in failed"))
  }

  @Test
  fun `message without a status code yields null`() {
    assertNull(SignInFailureClassifier.statusCodeFromMessage("The flow has been cancelled."))
    assertNull(SignInFailureClassifier.statusCodeFromMessage(null))
    assertNull(SignInFailureClassifier.statusCodeFromMessage(""))
  }

  // ---- genuine cancellation ------------------------------------------------

  @Test
  fun `plain cancellation with nothing attached is a real user cancel`() {
    val outcome =
      SignInFailureClassifier.classify(snapshot(message = "The flow has been cancelled."))

    assertEquals(SignInFailureKind.USER_CANCELLED, outcome)
  }

  @Test
  fun `sign in cancelled status code is a real user cancel`() {
    val outcome = SignInFailureClassifier.classify(snapshot(statusCode = 12501))

    assertEquals(SignInFailureKind.USER_CANCELLED, outcome)
  }

  // ---- provider and configuration -----------------------------------------

  @Test
  fun `developer error status code is a provider configuration problem`() {
    assertEquals(
      SignInFailureKind.PROVIDER_CONFIGURATION,
      SignInFailureClassifier.classify(snapshot(statusCode = 10)),
    )
  }

  @Test
  fun `provider wording without a code is still a provider problem`() {
    val outcome = SignInFailureClassifier.classify(snapshot(message = "Account reauth failed."))

    assertEquals(SignInFailureKind.PROVIDER_CONFIGURATION, outcome)
  }

  @Test
  fun `provider configuration exception is classified as provider configuration`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.GetCredentialProviderConfigurationException",
          type = "androidx.credentials.type.GetCredentialProviderConfigurationException",
        )
      )

    assertEquals(SignInFailureKind.PROVIDER_CONFIGURATION, outcome)
  }

  // ---- other categories ----------------------------------------------------

  @Test
  fun `missing account is its own outcome`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.NoCredentialException",
          type = "androidx.credentials.type.NoCredentialException",
        )
      )

    assertEquals(SignInFailureKind.NO_ACCOUNT_AVAILABLE, outcome)
  }

  @Test
  fun `unsupported device is its own outcome`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.GetCredentialUnsupportedException",
          type = "androidx.credentials.type.GetCredentialUnsupportedException",
        )
      )

    assertEquals(SignInFailureKind.UNSUPPORTED, outcome)
  }

  @Test
  fun `network wording and network status code are network failures`() {
    assertEquals(
      SignInFailureKind.NETWORK,
      SignInFailureClassifier.classify(snapshot(message = "Unable to resolve host accounts.google.com")),
    )
    assertEquals(
      SignInFailureKind.NETWORK,
      SignInFailureClassifier.classify(snapshot(statusCode = 7)),
    )
  }

  @Test
  fun `unmapped failure is unknown rather than a cancel`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(
          className = "java.lang.IllegalStateException",
          type = null,
          message = "something unexpected",
        )
      )

    assertEquals(SignInFailureKind.UNKNOWN, outcome)
  }

  // ---- privacy -------------------------------------------------------------

  @Test
  fun `snapshot keeps no token or account identity`() {
    val error = RuntimeException("failed for eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig")
    val snapshot = SignInFailureClassifier.snapshotFrom(error)

    assertEquals("java.lang.RuntimeException", snapshot.exceptionClassName)
    assertEquals(null, snapshot.credentialType)
    assertNull(snapshot.statusCode)
  }
}
