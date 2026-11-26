package com.example.domain.usecase

import com.example.domain.model.ThemePreference
import com.example.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveThemePreferenceUseCase
@Inject
constructor(private val repository: UserPreferencesRepository) {
  operator fun invoke(): Flow<ThemePreference> = repository.observeThemePreference()
}

class SetThemePreferenceUseCase
@Inject
constructor(private val repository: UserPreferencesRepository) {
  suspend operator fun invoke(preference: ThemePreference) {
    repository.setThemePreference(preference)
  }
}
