package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.EmailReportSettings
import com.example.domain.model.DailyAutoEntryReportSettings
import com.example.domain.model.ReportFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reportPreferencesDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "skyplus_report_preferences")

/**
 * Local DataStore for non-sensitive, user-controlled email report summary preferences.
 * Scoped strictly per authenticated Firebase UID.
 */
@Singleton
class ReportPreferencesDataSource @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private fun enabledKey(userId: String) = booleanPreferencesKey("${userId}_email_reports_enabled")

  private fun frequencyKey(userId: String) = stringPreferencesKey("${userId}_email_report_frequency")

  private fun recipientKey(userId: String) = stringPreferencesKey("${userId}_email_report_recipient")

  private fun lastPreparedKey(userId: String) = longPreferencesKey("${userId}_email_report_last_prepared")


  private fun dailyEnabledKey(userId: String) = booleanPreferencesKey("${userId}_daily_auto_entry_report_enabled")

  private fun dailyHourKey(userId: String) = intPreferencesKey("${userId}_daily_auto_entry_report_hour")

  private fun dailyMinuteKey(userId: String) = intPreferencesKey("${userId}_daily_auto_entry_report_minute")

  fun observeDailyAutoEntrySettings(userId: String): Flow<DailyAutoEntryReportSettings> {
    if (userId.isBlank()) return flowOf(DailyAutoEntryReportSettings())
    return context.reportPreferencesDataStore.data.map { prefs ->
      DailyAutoEntryReportSettings(
        enabled = prefs[dailyEnabledKey(userId)] ?: false,
        hourOfDay = (prefs[dailyHourKey(userId)] ?: 21).coerceIn(0, 23),
        minute = (prefs[dailyMinuteKey(userId)] ?: 0).coerceIn(0, 59),
      )
    }
  }

  suspend fun setDailyAutoEntrySettings(userId: String, settings: DailyAutoEntryReportSettings) {
    if (userId.isBlank()) return
    context.reportPreferencesDataStore.edit { prefs ->
      prefs[dailyEnabledKey(userId)] = settings.enabled
      prefs[dailyHourKey(userId)] = settings.hourOfDay
      prefs[dailyMinuteKey(userId)] = settings.minute
    }
  }

  fun observeSettings(userId: String): Flow<EmailReportSettings> {
    if (userId.isBlank()) return flowOf(EmailReportSettings())
    return context.reportPreferencesDataStore.data.map { prefs ->
      val freq =
        prefs[frequencyKey(userId)]
          ?.let { raw -> runCatching { ReportFrequency.valueOf(raw) }.getOrNull() }
          ?: ReportFrequency.MONTHLY
      EmailReportSettings(
        enabled = prefs[enabledKey(userId)] ?: false,
        frequency = freq,
        recipientEmail = prefs[recipientKey(userId)] ?: "",
        lastPreparedAtMillis = prefs[lastPreparedKey(userId)],
      )
    }
  }

  suspend fun setEnabled(userId: String, enabled: Boolean) {
    if (userId.isBlank()) return
    context.reportPreferencesDataStore.edit { prefs ->
      prefs[enabledKey(userId)] = enabled
    }
  }

  suspend fun setFrequency(userId: String, frequency: ReportFrequency) {
    if (userId.isBlank()) return
    context.reportPreferencesDataStore.edit { prefs ->
      prefs[frequencyKey(userId)] = frequency.name
    }
  }

  suspend fun setRecipientEmail(userId: String, email: String) {
    if (userId.isBlank()) return
    context.reportPreferencesDataStore.edit { prefs ->
      prefs[recipientKey(userId)] = email.trim()
    }
  }

  suspend fun recordPreparedAt(userId: String, timestampMillis: Long) {
    if (userId.isBlank()) return
    context.reportPreferencesDataStore.edit { prefs ->
      prefs[lastPreparedKey(userId)] = timestampMillis
    }
  }
}
