package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProfilePreferencesDataSource
import com.example.domain.model.ThemePreference
import com.example.domain.repository.WelcomeEmailSender
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.SetThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  observeThemePreferenceUseCase: ObserveThemePreferenceUseCase,
  private val setThemePreferenceUseCase: SetThemePreferenceUseCase,
  private val profilePreferences: ProfilePreferencesDataSource,
  private val welcomeEmailSender: WelcomeEmailSender,
) : ViewModel() {

  val themePreference: StateFlow<ThemePreference> =
    observeThemePreferenceUseCase()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemePreference.DARK,
      )

  private val _profileFirstName = MutableStateFlow("")
  val profileFirstName = _profileFirstName.asStateFlow()
  private var profileObservationJob: Job? = null
  private var observedProfileUid: String? = null

  fun bindProfile(uid: String, googleDisplayName: String?) {
    if (uid.isBlank() || observedProfileUid == uid) return
    observedProfileUid = uid
    profileObservationJob?.cancel()
    profileObservationJob = viewModelScope.launch {
      profilePreferences.ensureFirstName(uid, googleDisplayName)
      val initialName = profilePreferences.observeFirstName(uid).first().orEmpty()
      if (initialName.isNotBlank()) runCatching { welcomeEmailSender.sendWelcomeEmail(initialName) }
      profilePreferences.observeFirstName(uid).collect { savedName ->
        _profileFirstName.value = savedName.orEmpty()
      }
    }
  }

  fun saveProfileFirstName(uid: String, firstName: String) {
    if (uid.isBlank()) return
    viewModelScope.launch {
      runCatching { profilePreferences.saveFirstName(uid, firstName) }
        .onSuccess { _profileFirstName.value = firstName.trim() }
    }
  }

  fun onThemeSelected(preference: ThemePreference) {
    viewModelScope.launch { setThemePreferenceUseCase(preference) }
  }
}
