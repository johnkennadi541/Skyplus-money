package com.example.data.remote

/**
 * Remote provider integration status contract.
 * External Firebase Auth, Firestore, Facebook Login, and Google Drive credentials
 * are intentionally deferred to Phases 1 and 5 and must never be simulated.
 */
data class ExternalIntegrationStatus(
  val providerName: String,
  val targetPhase: Int,
  val isConfigured: Boolean,
  val missingRequirements: List<String>,
)
