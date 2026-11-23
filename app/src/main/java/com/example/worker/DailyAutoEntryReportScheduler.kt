package com.example.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.domain.model.DailyAutoEntryReportSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyAutoEntryReportScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun updateSchedule(userId: String, settings: DailyAutoEntryReportSettings, appendAfterCurrent: Boolean = false) {
    if (userId.isBlank()) return
    val workManager = WorkManager.getInstance(context)
    val uniqueName = uniqueWorkName(userId)
    if (!settings.enabled) {
      workManager.cancelUniqueWork(uniqueName)
      return
    }

    val now = Calendar.getInstance()
    val nextRun = (now.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, settings.hourOfDay)
      set(Calendar.MINUTE, settings.minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val reportDayStart = (nextRun.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val delayMillis = (nextRun.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    val request = OneTimeWorkRequestBuilder<DailyAutoEntryReportWorker>()
      .setInputData(
        Data.Builder()
          .putString(DailyAutoEntryReportWorker.KEY_USER_ID, userId)
          .putLong(DailyAutoEntryReportWorker.KEY_REPORT_DAY_START, reportDayStart)
          .build(),
      )
      .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
      .build()

    val policy = if (appendAfterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
    workManager.enqueueUniqueWork(uniqueName, policy, request)
  }

  fun cancel(userId: String) {
    if (userId.isNotBlank()) {
      WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(userId))
    }
  }

  private fun uniqueWorkName(userId: String) = "daily_auto_entry_report_$userId"
}
