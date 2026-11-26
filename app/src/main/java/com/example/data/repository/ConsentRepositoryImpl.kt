package com.example.data.repository

import com.example.data.local.ConsentPreferencesDataSource
import com.example.domain.model.ConsentRecord
import com.example.domain.repository.ConsentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentRepositoryImpl
@Inject
constructor(
  private val dataSource: ConsentPreferencesDataSource,
) : ConsentRepository {

  override suspend fun getConsent(uid: String): ConsentRecord? = dataSource.getConsent(uid)

  override suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String) {
    dataSource.saveConsent(uid, termsVersion, privacyVersion, System.currentTimeMillis())
  }

  override suspend fun clearConsent(uid: String) {
    dataSource.clearConsent(uid)
  }
}
