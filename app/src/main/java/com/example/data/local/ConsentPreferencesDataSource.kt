package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ConsentRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.skyplusConsentDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_consent_preferences")

@Singleton
class ConsentPreferencesDataSource @Inject constructor(@ApplicationContext private val context: Context) {

  suspend fun getConsent(uid: String): ConsentRecord? {
    val prefs = context.skyplusConsentDataStore.data.first()
    val termsKey = stringPreferencesKey("terms_version_$uid")
    val privacyKey = stringPreferencesKey("privacy_version_$uid")
    val timeKey = longPreferencesKey("timestamp_$uid")

    val terms = prefs[termsKey]
    val privacy = prefs[privacyKey]
    val timestamp = prefs[timeKey]

    if (terms == null || privacy == null || timestamp == null) {
      return null
    }

    return ConsentRecord(
      termsVersion = terms,
      privacyVersion = privacy,
      timestamp = timestamp,
    )
  }

  suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String, timestamp: Long) {
    context.skyplusConsentDataStore.edit { prefs ->
      prefs[stringPreferencesKey("terms_version_$uid")] = termsVersion
      prefs[stringPreferencesKey("privacy_version_$uid")] = privacyVersion
      prefs[longPreferencesKey("timestamp_$uid")] = timestamp
    }
  }

  suspend fun clearConsent(uid: String) {
    context.skyplusConsentDataStore.edit { prefs ->
      prefs.remove(stringPreferencesKey("terms_version_$uid"))
      prefs.remove(stringPreferencesKey("privacy_version_$uid"))
      prefs.remove(longPreferencesKey("timestamp_$uid"))
    }
  }
}
