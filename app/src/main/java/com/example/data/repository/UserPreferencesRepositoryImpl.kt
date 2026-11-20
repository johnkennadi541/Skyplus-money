package com.example.data.repository

import com.example.data.local.ThemePreferencesDataSource
import com.example.domain.model.ThemePreference
import com.example.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserPreferencesRepositoryImpl
@Inject
constructor(private val dataSource: ThemePreferencesDataSource) : UserPreferencesRepository {
  override fun observeThemePreference(): Flow<ThemePreference> = dataSource.themePreference

  override suspend fun setThemePreference(preference: ThemePreference) {
    dataSource.setThemePreference(preference)
  }
}
