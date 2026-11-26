package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.dashboardLayoutDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_dashboard_layout")

/**
 * Raw, per-UID dashboard layout as stored on disk.
 *
 * The strings are encoded by the dashboard layer, not here, so this data source stays a dumb
 * key-value store and knows nothing about the card model.
 */
data class DashboardLayoutSnapshot(
  val orderRaw: String? = null,
  val hiddenRaw: String? = null,
)

/**
 * Local DataStore for the Home dashboard card order and visibility.
 *
 * Every key is prefixed with the authenticated UID, exactly like [ReportPreferencesDataSource], so
 * one user's layout can never be read by another account on the same device. Nothing sensitive is
 * stored here: only card names.
 */
@Singleton
class DashboardLayoutPreferencesDataSource
@Inject
constructor(@ApplicationContext private val context: Context) {

  private fun orderKey(userId: String) = stringPreferencesKey("${userId}_dashboard_card_order")

  private fun hiddenKey(userId: String) = stringPreferencesKey("${userId}_dashboard_hidden_cards")

  fun observeLayout(userId: String): Flow<DashboardLayoutSnapshot> {
    if (userId.isBlank()) return flowOf(DashboardLayoutSnapshot())
    return context.dashboardLayoutDataStore.data.map { prefs ->
      DashboardLayoutSnapshot(
        orderRaw = prefs[orderKey(userId)],
        hiddenRaw = prefs[hiddenKey(userId)],
      )
    }
  }

  suspend fun saveLayout(userId: String, orderRaw: String, hiddenRaw: String) {
    if (userId.isBlank()) return
    context.dashboardLayoutDataStore.edit { prefs ->
      prefs[orderKey(userId)] = orderRaw
      prefs[hiddenKey(userId)] = hiddenRaw
    }
  }
}
