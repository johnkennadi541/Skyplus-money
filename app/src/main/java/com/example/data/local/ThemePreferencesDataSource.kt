package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.skyplusPreferencesDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_ui_preferences")

/**
 * Local DataStore datasource for non-sensitive UI preferences only (such as Dark/Light theme).
 * Sensitive financial records and consent tokens belong in encrypted Room/Keystore storage (Phases 1-2).
 */
@Singleton
class ThemePreferencesDataSource @Inject constructor(@ApplicationContext private val context: Context) {

  private val themeKey = stringPreferencesKey("ocean_theme_preference")

  val themePreference: Flow<ThemePreference> =
    context.skyplusPreferencesDataStore.data.map { prefs ->
      when (prefs[themeKey]) {
        ThemePreference.DARK.name -> ThemePreference.DARK
        ThemePreference.LIGHT.name -> ThemePreference.LIGHT
        ThemePreference.SYSTEM.name -> ThemePreference.SYSTEM
        else -> ThemePreference.DARK
      }
    }

  suspend fun setThemePreference(preference: ThemePreference) {
    context.skyplusPreferencesDataStore.edit { prefs -> prefs[themeKey] = preference.name }
  }
}
