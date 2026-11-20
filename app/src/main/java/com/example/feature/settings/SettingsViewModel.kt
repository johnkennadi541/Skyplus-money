package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ThemePreference
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.SetThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  observeThemePreferenceUseCase: ObserveThemePreferenceUseCase,
  private val setThemePreferenceUseCase: SetThemePreferenceUseCase,
) : ViewModel() {

  val themePreference: StateFlow<ThemePreference> =
    observeThemePreferenceUseCase()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemePreference.DARK,
      )

  fun onThemeSelected(preference: ThemePreference) {
    viewModelScope.launch { setThemePreferenceUseCase(preference) }
  }
}
