package com.example.domain.usecase

import com.example.domain.model.ConsentRecord
import com.example.domain.repository.ConsentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckUserConsentUseCase @Inject constructor(private val repository: ConsentRepository) {
  suspend operator fun invoke(uid: String): ConsentRecord? {
    if (uid.isBlank()) return null
    return repository.getConsent(uid)
  }
}

@Singleton
class SaveUserConsentUseCase @Inject constructor(private val repository: ConsentRepository) {
  suspend operator fun invoke(uid: String, termsVersion: String, privacyVersion: String) {
    if (uid.isBlank()) return
    repository.saveConsent(uid, termsVersion, privacyVersion)
  }
}
