package com.example.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppFoundationConfig
import com.example.core.security.DefaultSecurityPolicy
import com.example.domain.model.Money
import com.example.domain.model.PhaseDeliveryState
import com.example.domain.model.RoadmapPhaseItem
import com.example.domain.model.ThemePreference
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.SetThemePreferenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeFoundationUiState(
  val appName: String = AppFoundationConfig.APP_NAME,
  val tagline: String = AppFoundationConfig.TAGLINE,
  val phaseLabel: String = AppFoundationConfig.CURRENT_PHASE_LABEL,
  val packageId: String = AppFoundationConfig.EXPECTED_PACKAGE_ID,
  val themePreference: ThemePreference = ThemePreference.DARK,
  val mandatoryAuthEnforced: Boolean = DefaultSecurityPolicy.requiresMandatoryAuthentication,
  val zeroGuestMode: Boolean = !DefaultSecurityPolicy.allowsGuestMode,
  val formattedPrecisionSample: String = "$0.00",
  val roadmap: List<RoadmapPhaseItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  observeThemePreferenceUseCase: ObserveThemePreferenceUseCase,
  private val setThemePreferenceUseCase: SetThemePreferenceUseCase,
  private val formatMoneyUseCase: FormatMoneyUseCase,
) : ViewModel() {

  private val zeroUsdSample: String = formatMoneyUseCase(Money.zero("USD"), Locale.US)

  private val foundationRoadmap =
    listOf(
      RoadmapPhaseItem(
        0,
        "Phase 0 — Foundation",
        "Clean Architecture, Hilt DI, Ocean Design System, Navigation, and Money precision engine.",
        PhaseDeliveryState.COMPLETE_IN_PROGRESS,
      ),
      RoadmapPhaseItem(
        1,
        "Phase 1 — Authentication & Consent",
        "Mandatory Firebase Google Sign-In, zero Guest Mode, and v1.0.0 Legal Consent Gate.",
        PhaseDeliveryState.COMPLETE_IN_PROGRESS,
      ),
      RoadmapPhaseItem(
        2,
        "Phase 2 — Core Finance & Smart Detection",
        "Multi-currency accounts, ledger, atomic transfers, SQLCipher Room, and on-device detection.",
        PhaseDeliveryState.COMPLETE_IN_PROGRESS,
      ),
      RoadmapPhaseItem(
        3,
        "Phase 3 — Financial Management",
        "Budgets, savings goals, loans, EMI schedules, credit cards, and spending insights.",
        PhaseDeliveryState.PLANNED_NEXT,
      ),
      RoadmapPhaseItem(
        4,
        "Phase 4 — Advanced Finance",
        "Investments, gold, fixed deposits, net worth, shopping lists, charts, PDF/DOCX/CSV.",
        PhaseDeliveryState.SCHEDULED,
      ),
      RoadmapPhaseItem(
        5,
        "Phase 5 — Cloud & Backup",
        "Optional Firestore cloud sync, conflict resolution, and encrypted Google Drive backup.",
        PhaseDeliveryState.SCHEDULED,
      ),
      RoadmapPhaseItem(
        6,
        "Phase 6 — Final Release",
        "Regression suite, security & migration audit, APK signature verification, release.",
        PhaseDeliveryState.SCHEDULED,
      ),
    )

  val uiState: StateFlow<HomeFoundationUiState> =
    observeThemePreferenceUseCase()
      .map { pref ->
        HomeFoundationUiState(
          themePreference = pref,
          formattedPrecisionSample = zeroUsdSample,
          roadmap = foundationRoadmap,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue =
          HomeFoundationUiState(
            formattedPrecisionSample = zeroUsdSample,
            roadmap = foundationRoadmap,
          ),
      )

  fun toggleDarkLightTheme() {
    viewModelScope.launch {
      val next =
        when (uiState.value.themePreference) {
          ThemePreference.DARK -> ThemePreference.LIGHT
          ThemePreference.LIGHT -> ThemePreference.DARK
          ThemePreference.SYSTEM -> ThemePreference.LIGHT
        }
      setThemePreferenceUseCase(next)
    }
  }
}
