package com.example

import com.example.domain.model.ThemePreference
import com.example.domain.repository.UserPreferencesRepository
import com.example.domain.usecase.FormatMoneyUseCase
import com.example.domain.usecase.ObserveThemePreferenceUseCase
import com.example.domain.usecase.SetThemePreferenceUseCase
import com.example.feature.dashboard.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private class FakePreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow(ThemePreference.DARK)

    override fun observeThemePreference(): Flow<ThemePreference> = state

    override suspend fun setThemePreference(preference: ThemePreference) {
      state.value = preference
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `home view model exposes foundation security posture and toggles theme`() = runTest {
    val repo = FakePreferencesRepository()
    val viewModel =
      HomeViewModel(
        observeThemePreferenceUseCase = ObserveThemePreferenceUseCase(repo),
        setThemePreferenceUseCase = SetThemePreferenceUseCase(repo),
        formatMoneyUseCase = FormatMoneyUseCase(),
      )

    val collector = launch { viewModel.uiState.collect() }
    advanceUntilIdle()

    assertEquals("com.skyplus.money", viewModel.uiState.value.packageId)
    assertTrue(viewModel.uiState.value.mandatoryAuthEnforced)
    assertTrue(viewModel.uiState.value.zeroGuestMode)
    assertEquals(7, viewModel.uiState.value.roadmap.size)
    assertEquals(ThemePreference.DARK, viewModel.uiState.value.themePreference)

    viewModel.toggleDarkLightTheme()
    advanceUntilIdle()
    assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.themePreference)

    collector.cancel()
  }
}
