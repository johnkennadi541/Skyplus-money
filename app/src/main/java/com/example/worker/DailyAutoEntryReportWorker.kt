package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.local.ReportPreferencesDataSource
import com.example.data.local.db.dao.TransactionDetectionEventDao
import com.example.domain.model.AuthSessionState
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@HiltWorker
class DailyAutoEntryReportWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val authRepository: AuthRepository,
  private val eventDao: TransactionDetectionEventDao,
  private val reportPreferences: ReportPreferencesDataSource,
  private val scheduler: DailyAutoEntryReportScheduler,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val userId = inputData.getString(KEY_USER_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
    val session = authRepository.observeAuthSession().first()
    val authenticatedUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (authenticatedUid != userId) return Result.success()

    val settings = reportPreferences.observeDailyAutoEntrySettings(userId).first()
    if (!settings.enabled) return Result.success()

    val reportDayStart = inputData.getLong(KEY_REPORT_DAY_START, startOfToday())
    val dayEnd = (Calendar.getInstance().apply { timeInMillis = reportDayStart }).apply {
      add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    val events = eventDao.getAllEvents(userId).first()
      .map { it.toDomain() }
      .filter { it.receivedAt >= reportDayStart && it.receivedAt < dayEnd }

    postDailyReport(userId, reportDayStart, events)

    // Schedule the next local-time report even if Android currently suppresses notifications.
    val latestSettings = reportPreferences.observeDailyAutoEntrySettings(userId).first()
    if (latestSettings.enabled) scheduler.updateSchedule(userId, latestSettings, appendAfterCurrent = true)
    return Result.success()
  }

  private fun postDailyReport(
    userId: String,
    reportDayStart: Long,
    events: List<TransactionDetectionEvent>,
  ) {
    val autoSaved = events.filter { it.status == DetectionEventStatus.AUTO_SAVED && it.createdTransactionId != null }
    val exceptions = events.count { it.status == DetectionEventStatus.IN_REVIEW }
    val duplicates = events.count { it.status == DetectionEventStatus.DUPLICATE }

    val currencies = autoSaved.groupBy { it.detectedCurrency }.toSortedMap()
    val totals = currencies.map { (currency, currencyEvents) ->
      val income = sumMinor(currencyEvents.filter { it.detectedType in setOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL) })
      val expense = sumMinor(currencyEvents.filter { it.detectedType == TransactionType.EXPENSE })
      val incomeCount = currencyEvents.count { it.detectedType in setOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL) }
      val expenseCount = currencyEvents.count { it.detectedType == TransactionType.EXPENSE }
      "$currency  Income: ${formatMinor(income, currency)} ($incomeCount)  •  Expense: ${formatMinor(expense, currency)} ($expenseCount)"
    }

    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reportDayStart))
    val entryLines = autoSaved
      .sortedBy { it.receivedAt }
      .take(8)
      .map { event ->
        val direction = when (event.detectedType) {
          TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL -> "INCOME"
          TransactionType.EXPENSE -> "EXPENSE"
          else -> event.detectedType.name
        }
        val title = event.detectedMerchant ?: event.senderOrTitle
        val accountHint = event.detectedAccountLast4?.let { " • A/c ••$it" }.orEmpty()
        "$direction • $title • ${formatMinor(event.detectedAmountMinor, event.detectedCurrency)} ${event.detectedCurrency}$accountHint"
      }

    val body = buildString {
      append("Automatically posted: ${autoSaved.size}")
      if (totals.isNotEmpty()) {
        append("\n")
        append(totals.joinToString("\n"))
      } else {
        append("\nNo automatic entries were posted.")
      }
      if (entryLines.isNotEmpty()) {
        append("\n\nEntries:")
        append("\n")
        append(entryLines.joinToString("\n"))
        if (autoSaved.size > entryLines.size) {
          append("\n+ ${autoSaved.size - entryLines.size} more entries")
        }
      }
      append("\n\nNeeds attention: $exceptions")
      append("\nDuplicates skipped: $duplicates")
      append("\nSource: SMS notifications only")
    }

    runCatching {
      ensureNotificationChannel()
      val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        applicationContext,
        userId.hashCode(),
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_report_image)
        .setContentTitle("SKYPLUS MONEY • Daily Auto-Entry Report")
        .setContentText("$date — ${autoSaved.size} entries • $exceptions need attention")
        .setStyle(NotificationCompat.BigTextStyle().bigText("$date\n$body"))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
      NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = applicationContext.getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          "Daily Auto-Entry Reports",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Daily summary of automatically recorded income and expenses."
        },
      )
    }
  }

  private fun sumMinor(events: List<TransactionDetectionEvent>): Long =
    events.fold(0L) { total, event -> Math.addExact(total, event.detectedAmountMinor) }

  private fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val digits = runCatching { java.util.Currency.getInstance(currencyCode).defaultFractionDigits }
      .getOrDefault(2)
      .coerceAtLeast(0)
    return BigDecimal.valueOf(amountMinor, digits).toPlainString()
  }

  private fun startOfToday(): Long =
    Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

  companion object {
    const val KEY_USER_ID = "daily_report_user_id"
    const val KEY_REPORT_DAY_START = "daily_report_day_start"
    const val CHANNEL_ID = "skyplus_daily_auto_entry_report"
    const val NOTIFICATION_ID = 73104
  }
}
