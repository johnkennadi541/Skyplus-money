package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.ReportPreferencesDataSource
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DailyAutoEntryReportTimeChangeReceiver : BroadcastReceiver() {

  @Inject lateinit var authRepository: AuthRepository
  @Inject lateinit var reportPreferences: ReportPreferencesDataSource
  @Inject lateinit var scheduler: DailyAutoEntryReportScheduler
  @Inject lateinit var creditCardReminderScheduler: CreditCardReminderScheduler

  override fun onReceive(context: Context, intent: Intent) {
    if (
      intent.action != Intent.ACTION_TIME_CHANGED &&
        intent.action != Intent.ACTION_TIMEZONE_CHANGED
    ) return

    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      try {
        runCatching {
          val session = authRepository.observeAuthSession().first()
          val userId = (session as? AuthSessionState.Authenticated)?.user?.uid ?: return@runCatching
          val settings = reportPreferences.observeDailyAutoEntrySettings(userId).first()
          scheduler.updateSchedule(userId, settings)
          creditCardReminderScheduler.updateSchedule(userId)
        }
      } finally {
        pendingResult.finish()
        scope.cancel()
      }
    }
  }
}
