package com.example

import com.example.data.remote.AuthDiagnosticClassifier
import com.example.data.remote.AuthDiagnosticOutcome
import com.example.data.remote.AuthDiagnosticSanitizer
import com.example.data.remote.AuthFailureSnapshot
import com.example.data.remote.AuthSignInDiagnostics
import com.example.data.remote.AuthDiagnosticStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the DEBUG-only Google Sign-In diagnostics.
 *
 * These tests protect two guarantees:
 * 1. Nothing sensitive (ID token, email, OAuth client id) can reach a log line or the debug UI.
 * 2. A cancellation is not blindly reported as "user cancelled" when it carries a
 *    configuration or developer status code.
 */
class AuthSignInDiagnosticsTest {

  // ---- Sanitiser ----------------------------------------------------------------

  @Test
  fun sanitizerRedactsJwtLikeToken() {
    val raw = "Token failed: eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiY2RlZjEyMyJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature_part"
    val cleaned = AuthDiagnosticSanitizer.sanitize(raw)

    assertNotNull(cleaned)
    assertFalse("JWT must never survive sanitising", cleaned!!.contains("eyJhbGciOiJSUzI1NiI"))
    assertTrue(cleaned.contains("<redacted-token>"))
  }

  @Test
  fun sanitizerRedactsEmailAddress() {
    val cleaned = AuthDiagnosticSanitizer.sanitize("No account for john.doe@example.com on device")

    assertNotNull(cleaned)
    assertFalse(cleaned!!.contains("john.doe@example.com"))
    assertTrue(cleaned.contains("<redacted-email>"))
  }

  @Test
  fun sanitizerRedactsOAuthClientId() {
    val cleaned =
      AuthDiagnosticSanitizer.sanitize(
        "Client 735404789471-5c1ok63ng15plb3d2p87j4br3a1c7g16.apps.googleusercontent.com rejected"
      )

    assertNotNull(cleaned)
    assertFalse(cleaned!!.contains("5c1ok63ng15plb3d2p87j4br3a1c7g16"))
    assertTrue(cleaned.contains("<redacted-client-id>") || cleaned.contains("<redacted>"))
  }

  @Test
  fun sanitizerKeepsShortHarmlessMessageReadable() {
    val cleaned = AuthDiagnosticSanitizer.sanitize("The flow has been cancelled.")

    assertTrue(cleaned == "The flow has been cancelled.")
  }

  @Test
  fun sanitizerHandlesNullAndBlank() {
    assertNull(AuthDiagnosticSanitizer.sanitize(null))
    assertNull(AuthDiagnosticSanitizer.sanitize("   "))
  }

  // ---- Classifier ---------------------------------------------------------------

  @Test
  fun plainCancellationWithoutStatusCodeIsUserCancellation() {
    val outcome = AuthDiagnosticClassifier.classify(cancellationSnapshot(statusCode = null))

    assertEquals(AuthDiagnosticOutcome.CANCELLED_BY_USER, outcome)
  }

  @Test
  fun cancellationCarryingDeveloperErrorCodeIsNotReportedAsUserCancellation() {
    val outcome = AuthDiagnosticClassifier.classify(cancellationSnapshot(statusCode = 10))

    assertEquals(AuthDiagnosticOutcome.DEVELOPER_ERROR, outcome)
  }

  @Test
  fun cancellationCarryingSignInFailedCodeIsProviderConfiguration() {
    val outcome = AuthDiagnosticClassifier.classify(cancellationSnapshot(statusCode = 12500))

    assertEquals(AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR, outcome)
  }

  @Test
  fun cancellationWithProviderFailureInCauseChainIsFlaggedAsConfigSuspect() {
    val snapshot =
      cancellationSnapshot(statusCode = null).copy(causeChain = listOf("ApiException", "RuntimeException"))
    val outcome = AuthDiagnosticClassifier.classify(snapshot)

    assertEquals(AuthDiagnosticOutcome.CANCELLED_WITH_CONFIG_SUSPECT, outcome)
  }

  @Test
  fun noCredentialExceptionIsItsOwnOutcome() {
    val outcome =
      AuthDiagnosticClassifier.classify(
        AuthFailureSnapshot(exceptionClassName = "androidx.credentials.exceptions.NoCredentialException")
      )

    assertEquals(AuthDiagnosticOutcome.NO_CREDENTIAL_AVAILABLE, outcome)
  }

  @Test
  fun providerConfigurationExceptionIsNotUserCancellation() {
    val outcome =
      AuthDiagnosticClassifier.classify(
        AuthFailureSnapshot(
          exceptionClassName = "androidx.credentials.exceptions.GetCredentialProviderConfigurationException"
        )
      )

    assertEquals(AuthDiagnosticOutcome.PROVIDER_CONFIGURATION_ERROR, outcome)
  }

  @Test
  fun networkFailureIsClassifiedAsNetworkError() {
    val outcome =
      AuthDiagnosticClassifier.classify(
        AuthFailureSnapshot(
          exceptionClassName = "androidx.credentials.exceptions.GetCredentialUnknownException",
          message = "Unable to resolve host accounts.google.com",
        )
      )

    assertEquals(AuthDiagnosticOutcome.NETWORK_ERROR, outcome)
  }

  @Test
  fun unmappedFailureIsUnknownNotCancellation() {
    val outcome =
      AuthDiagnosticClassifier.classify(
        AuthFailureSnapshot(exceptionClassName = "java.lang.IllegalStateException", message = "boom")
      )

    assertEquals(AuthDiagnosticOutcome.UNKNOWN_ERROR, outcome)
  }

  @Test
  fun everyOutcomeHasAHint() {
    AuthDiagnosticOutcome.entries.forEach { outcome ->
      assertTrue(
        "Missing hint for $outcome",
        AuthDiagnosticClassifier.hintFor(outcome).isNotBlank(),
      )
    }
  }

  // ---- Debug record -------------------------------------------------------------

  @Test
  fun describeCarriesNoTokenOrEmail() {
    val record =
      AuthSignInDiagnostics(
        stage = AuthDiagnosticStage.CREDENTIAL_REQUEST,
        outcome = AuthDiagnosticOutcome.DEVELOPER_ERROR,
        exceptionClassName = "androidx.credentials.exceptions.GetCredentialCancellationException",
        credentialExceptionType = "androidx.credentials.type.GetCredentialCancellationException",
        statusCode = 10,
        message = AuthDiagnosticSanitizer.sanitize("id_token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig"),
        firebaseErrorCode = "ERROR_INVALID_CREDENTIAL",
        packageName = "com.skyplus.money",
        signingSha1 = "C1:0D:77:8E:C3:C0:FA:2E:46:17:D5:02:34:A1:DC:22:CB:B7:06:C3",
        webClientIdConfigured = true,
      )

    val line = record.describe()

    assertTrue(line.contains("statusCode=10"))
    assertTrue(line.contains("outcome=DEVELOPER_ERROR"))
    assertFalse(line.contains("eyJhbGciOiJSUzI1NiJ9"))
  }

  private fun cancellationSnapshot(statusCode: Int?): AuthFailureSnapshot =
    AuthFailureSnapshot(
      exceptionClassName = "androidx.credentials.exceptions.GetCredentialCancellationException",
      credentialExceptionType = "androidx.credentials.type.GetCredentialCancellationException",
      statusCode = statusCode,
      message = "The flow has been cancelled.",
    )
}
