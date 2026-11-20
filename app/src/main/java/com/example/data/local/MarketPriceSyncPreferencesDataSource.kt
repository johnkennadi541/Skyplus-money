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

  private val autoConsentKey = booleanPreferencesKey("auto_update_consent_enabled")
  private val wifiOnlyKey = booleanPreferencesKey("sync_only_on_wifi")
  private val lastSyncMillisKey = longPreferencesKey("last_sync_attempt_millis")
  private val lastSyncStatusKey = stringPreferencesKey("last_sync_status_message")

  val syncSettings: Flow<MarketSyncSettings> =
    context.marketSyncDataStore.data.map { prefs ->
      MarketSyncSettings(
        autoUpdateConsentEnabled = prefs[autoConsentKey] ?: false,
        syncOnlyOnWifi = prefs[wifiOnlyKey] ?: true,
        lastSyncAttemptMillis = prefs[lastSyncMillisKey],
        lastSyncStatusMessage = prefs[lastSyncStatusKey],
      )
    }

  suspend fun setAutoUpdateConsent(enabled: Boolean) {
    context.marketSyncDataStore.edit { prefs ->
      prefs[autoConsentKey] = enabled
    }
  }

  suspend fun setSyncOnlyOnWifi(wifiOnly: Boolean) {
    context.marketSyncDataStore.edit { prefs ->
      prefs[wifiOnlyKey] = wifiOnly
    }
  }

  suspend fun recordSyncResult(timestampMillis: Long, statusMessage: String) {
    context.marketSyncDataStore.edit { prefs ->
      prefs[lastSyncMillisKey] = timestampMillis
      prefs[lastSyncStatusKey] = statusMessage
    }
  }
}
