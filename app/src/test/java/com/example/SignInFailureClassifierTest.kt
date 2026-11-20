package com.example

import com.example.data.remote.SignInFailureClassifier
import com.example.data.remote.SignInFailureKind
import com.example.data.remote.SignInFailureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app used to report every GetCredentialCancellationException as a genuine user
 * cancel. These tests pin the corrected behaviour.
 *
 * Review corrections covered here:
 * - status 16 alone is NOT a provider problem; only the verified "Account reauth
 *   failed" wording makes it one.
 * - the failure snapshot must not retain the raw exception message.
 */
class SignInFailureClassifierTest {

  private fun snapshot(
    className: String = "androidx.credentials.exceptions.GetCredentialCancellationException",
    type: String? = "androidx.credentials.type.GetCredentialCancellationException",
    statusCode: Int? = null,
    hasProviderReauthSignal: Boolean = false,
    hasNetworkSignal: Boolean = false,
    causeChain: List<String> = emptyList(),
  ) = SignInFailureSnapshot(
    exceptionClassName = className,
    credentialType = type,
    statusCode = statusCode,
    hasProviderReauthSignal = hasProviderReauthSignal,
    hasNetworkSignal = hasNetworkSignal,
    causeChain = causeChain,
  )

  // ---- the real production case --------------------------------------------

  @Test
  fun `status 16 with account reauth wording is a provider problem`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(statusCode = 16, hasProviderReauthSignal = true)
      )

    assertEquals(SignInFailureKind.PROVIDER_CONFIGURATION, outcome)
  }

  @Test
  fun `reauth wording without a status code is still a provider problem`() {
    val outcome = SignInFailureClassifier.classify(snapshot(hasProviderReauthSignal = true))

    assertEquals(SignInFailureKind.PROVIDER_CONFIGURATION, outcome)
  }

  // ---- generic status 16 must stay a user cancel ---------------------------

  @Test
  fun `generic status 16 without reauth wording is a user cancel`() {
    val outcome = SignInFailureClassifier.classify(snapshot(statusCode = 16))

    assertEquals(SignInFailureKind.USER_CANCELLED, outcome)
  }

  @Test
  fun `generic status 16 with an unrelated message is a user cancel`() {
    val outcome =
      SignInFailureClassifier.classify(
        snapshot(statusCode = 16, hasProviderReauthSignal = false, hasNetworkSignal = false)
      )

    assertEquals(SignInFailureKind.USER_CANCELLED, outcome)
  }

  // ---- genuine cancellation ------------------------------------------------

  @Test
  fun `plain cancellation with nothing attached is a real user cancel`() {
    assertEquals(
      SignInFailureKind.USER_CANCELLED,
      SignInFailureClassifier.classify(snapshot()),
    )
  }

  @Test
  fun `sign in cancelled status code is a real user cancel`() {
    assertEquals(
      SignInFailureKind.USER_CANCELLED,
      SignInFailureClassifier.classify(snapshot(statusCode = 12501)),
    )
  }

  @Test
  fun `only the exact reauth wording counts as the provider signal`() {
    assertTrue(SignInFailureClassifier.hasReauthFailedSignal("[16] Account reauth failed."))
    assertFalse(SignInFailureClassifier.hasReauthFailedSignal("The flow has been cancelled."))
    assertFalse(SignInFailureClassifier.hasReauthFailedSignal("cancelled by user"))
    assertFalse(SignInFailureClassifier.hasReauthFailedSignal(null))
  }

  // ---- status parsing ------------------------------------------------------

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

  // ---- other provider and category outcomes --------------------------------

  @Test
  fun `developer error status code is a provider configuration problem`() {
    assertEquals(
      SignInFailureKind.PROVIDER_CONFIGURATION,
      SignInFailureClassifier.classify(snapshot(statusCode = 10)),
    )
  }

  @Test
  fun `provider configuration exception is classified as provider configuration`() {
    assertEquals(
      SignInFailureKind.PROVIDER_CONFIGURATION,
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.GetCredentialProviderConfigurationException",
          type = "androidx.credentials.type.GetCredentialProviderConfigurationException",
        )
      ),
    )
  }

  @Test
  fun `missing account is its own outcome`() {
    assertEquals(
      SignInFailureKind.NO_ACCOUNT_AVAILABLE,
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.NoCredentialException",
          type = "androidx.credentials.type.NoCredentialException",
        )
      ),
    )
  }

  @Test
  fun `unsupported device is its own outcome`() {
    assertEquals(
      SignInFailureKind.UNSUPPORTED,
      SignInFailureClassifier.classify(
        snapshot(
          className = "androidx.credentials.exceptions.GetCredentialUnsupportedException",
          type = "androidx.credentials.type.GetCredentialUnsupportedException",
        )
      ),
    )
  }

  @Test
  fun `network signal and network status code are network failures`() {
    assertEquals(
      SignInFailureKind.NETWORK,
      SignInFailureClassifier.classify(snapshot(hasNetworkSignal = true)),
    )
    assertEquals(
      SignInFailureKind.NETWORK,
      SignInFailureClassifier.classify(snapshot(statusCode = 7)),
    )
  }

  @Test
  fun `unmapped failure is unknown rather than a cancel`() {
    assertEquals(
      SignInFailureKind.UNKNOWN,
      SignInFailureClassifier.classify(
        snapshot(className = "java.lang.IllegalStateException", type = null)
      ),
    )
  }

  // ---- privacy -------------------------------------------------------------

  @Test
  fun `snapshot retains no raw message, token or account identity`() {
    val error =
      RuntimeException(
        "[16] Account reauth failed for eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig " +
          "signed in as john.doe@example.com with token ya29.a0AfH6SMBsecretvalue"
      )

    val snapshot = SignInFailureClassifier.snapshotFrom(error)

    // The classification inputs survive...
    assertEquals(16, snapshot.statusCode)
    assertTrue(snapshot.hasProviderReauthSignal)

    // ...but nothing of the raw text does. The snapshot has no message field at all,
    // so its full textual form must be free of every sensitive fragment.
    val rendered = snapshot.toString()
    assertFalse("token leaked", rendered.contains("eyJ"))
    assertFalse("email leaked", rendered.contains("john.doe@example.com"))
    assertFalse("access token leaked", rendered.contains("ya29"))
    assertFalse("raw message leaked", rendered.contains("Account reauth failed for"))
  }
}
