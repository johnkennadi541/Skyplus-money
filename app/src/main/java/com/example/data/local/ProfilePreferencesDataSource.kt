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
import kotlinx.coroutines.flow.map

private val Context.skyplusProfileDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_profile_preferences")

/** Stores only a non-sensitive, UID-scoped profile display preference. */
@Singleton
class ProfilePreferencesDataSource @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun observeFirstName(uid: String): Flow<String?> {
    require(uid.isNotBlank())
    val key = stringPreferencesKey("${uid}_profile_first_name")
    return context.skyplusProfileDataStore.data.map { preferences ->
      preferences[key]?.takeIf(String::isNotBlank)
    }
  }

  suspend fun ensureFirstName(uid: String, googleDisplayName: String?) {
    require(uid.isNotBlank())
    val key = stringPreferencesKey("${uid}_profile_first_name")
    val suggested = googleDisplayName
      ?.trim()
      ?.split(Regex("\\s+"))
      ?.firstOrNull()
      ?.takeIf(String::isNotBlank)
      ?: return
    context.skyplusProfileDataStore.edit { preferences ->
      if (preferences[key].isNullOrBlank()) preferences[key] = suggested
    }
  }

  suspend fun saveFirstName(uid: String, firstName: String) {
    require(uid.isNotBlank())
    val normalized = firstName.trim()
    require(normalized.isNotBlank() && normalized.length <= 60)
    val key = stringPreferencesKey("${uid}_profile_first_name")
    context.skyplusProfileDataStore.edit { preferences ->
      preferences[key] = normalized
    }
  }
}
