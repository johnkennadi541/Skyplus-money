package com.example.domain.repository

import com.example.domain.model.ConsentRecord

interface ConsentRepository {
  suspend fun getConsent(uid: String): ConsentRecord?
  suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String)
  suspend fun clearConsent(uid: String)
}
