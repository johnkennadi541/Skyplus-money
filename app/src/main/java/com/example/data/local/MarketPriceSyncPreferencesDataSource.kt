package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.MarketSyncSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.marketSyncDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_market_sync_preferences")

/**
 * Local DataStore for non-sensitive optional market-price sync preferences.
 * Automatic market sync is strictly opt-in (disabled by default).
 */
@Singleton
class MarketPriceSyncPreferencesDataSource @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private fun autoConsentKey(userId: String) = booleanPreferencesKey("auto_update_consent_enabled_$userId")
  private fun wifiOnlyKey(userId: String) = booleanPreferencesKey("sync_only_on_wifi_$userId")
  private fun lastSyncMillisKey(userId: String) = longPreferencesKey("last_sync_attempt_millis_$userId")
  private fun lastSyncStatusKey(userId: String) = stringPreferencesKey("last_sync_status_message_$userId")

  fun syncSettings(userId: String): Flow<MarketSyncSettings> {
    require(userId.isNotBlank()) { "Authenticated user ID is required" }
    return context.marketSyncDataStore.data.map { prefs ->
      MarketSyncSettings(
        autoUpdateConsentEnabled = prefs[autoConsentKey(userId)] ?: false,
        syncOnlyOnWifi = prefs[wifiOnlyKey(userId)] ?: true,
        lastSyncAttemptMillis = prefs[lastSyncMillisKey(userId)],
        lastSyncStatusMessage = prefs[lastSyncStatusKey(userId)],
      )
    }
  }

  suspend fun setAutoUpdateConsent(userId: String, enabled: Boolean) {
    require(userId.isNotBlank()) { "Authenticated user ID is required" }
    context.marketSyncDataStore.edit { prefs ->
      prefs[autoConsentKey(userId)] = enabled
    }
  }

  suspend fun setSyncOnlyOnWifi(userId: String, wifiOnly: Boolean) {
    require(userId.isNotBlank()) { "Authenticated user ID is required" }
    context.marketSyncDataStore.edit { prefs ->
      prefs[wifiOnlyKey(userId)] = wifiOnly
    }
  }

  suspend fun recordSyncResult(userId: String, timestampMillis: Long, statusMessage: String) {
    require(userId.isNotBlank()) { "Authenticated user ID is required" }
    context.marketSyncDataStore.edit { prefs ->
      prefs[lastSyncMillisKey(userId)] = timestampMillis
      prefs[lastSyncStatusKey(userId)] = statusMessage
    }
  }
}
