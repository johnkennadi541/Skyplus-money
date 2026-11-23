package com.example.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditCardReminderScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun updateSchedule(userId: String, appendAfterCurrent: Boolean = false) {
    if (userId.isBlank()) return
    val manager = WorkManager.getInstance(context)
    val uniqueName = uniqueWorkName(userId)
    val now = Calendar.getInstance()
    val nextRun = (now.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, DEFAULT_HOUR)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val request = OneTimeWorkRequestBuilder<CreditCardReminderWorker>()
      .setInputData(Data.Builder().putString(CreditCardReminderWorker.KEY_USER_ID, userId).build())
      .setInitialDelay((nextRun.timeInMillis - now.timeInMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
      .build()
    manager.enqueueUniqueWork(
      uniqueName,
      if (appendAfterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun cancel(userId: String) {
    if (userId.isNotBlank()) WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(userId))
  }

  private fun uniqueWorkName(userId: String) = "credit_card_due_reminders_$userId"

  companion object {
    const val DEFAULT_HOUR = 9
  }
}
