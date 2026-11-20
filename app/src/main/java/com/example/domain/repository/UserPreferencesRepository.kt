package com.example.domain.repository

import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
  fun observeThemePreference(): Flow<ThemePreference>

  suspend fun setThemePreference(preference: ThemePreference)
}
