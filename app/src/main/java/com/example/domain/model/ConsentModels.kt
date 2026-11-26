package com.example.domain.model

data class ConsentRecord(
  val termsVersion: String,
  val privacyVersion: String,
  val timestamp: Long,
) {
  val isUpToDate: Boolean
    get() = termsVersion == CURRENT_TERMS_VERSION && privacyVersion == CURRENT_PRIVACY_VERSION

  companion object {
    const val CURRENT_TERMS_VERSION = "1.0.0"
    const val CURRENT_PRIVACY_VERSION = "1.0.0"
  }
}
