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
import com.example.data.local.db.dao.CreditCardDao
import com.example.data.local.db.dao.CreditCardStatementDao
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FinancialTransactionRepository
import com.example.domain.usecase.CreditCardStatementCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@HiltWorker
class CreditCardReminderWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val authRepository: AuthRepository,
  private val cardDao: CreditCardDao,
  private val statementDao: CreditCardStatementDao,
  private val transactionRepository: FinancialTransactionRepository,
  private val scheduler: CreditCardReminderScheduler,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val userId = inputData.getString(KEY_USER_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
    val session = authRepository.observeAuthSession().first()
    val currentUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (currentUid != userId) return Result.success()

    val today = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val windowEnd = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, MAX_LOOKAHEAD_DAYS) }
    val cards = cardDao.observeCards(userId).first().associateBy { it.id }
    val statements = statementDao.observeDueStatements(userId, today.timeInMillis, windowEnd.timeInMillis).first()
    val transactionsByAccount = mutableMapOf<String, List<com.example.domain.model.FinancialTransaction>>()

    statements.forEach { statement ->
      val card = cards[statement.cardId] ?: return@forEach
      if (!card.isActive || !card.reminderEnabled) return@forEach

      val zone = java.time.ZoneId.systemDefault()
      val todayLocal = java.time.Instant.ofEpochMilli(today.timeInMillis).atZone(zone).toLocalDate()
      val dueLocal = java.time.Instant.ofEpochMilli(statement.dueDate).atZone(zone).toLocalDate()
      val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(todayLocal, dueLocal).toInt()
      if (daysRemaining in 0..card.reminderDaysBefore) {
        val transactions = transactionsByAccount[card.accountId]
          ?: transactionRepository.getTransactionsByAccount(userId, card.accountId).first().also {
            transactionsByAccount[card.accountId] = it
          }
        val summary = CreditCardStatementCalculator.summarize(
          statements = listOf(statement.toDomain()),
          card = card.toDomain(),
          transactions = transactions,
        ).single()
        if (summary.remainingDueMinor > 0L) {
          postReminder(
            statementId = statement.id,
            issuer = card.issuerName,
            last4 = card.last4,
            dueDate = statement.dueDate,
            daysRemaining = daysRemaining,
            minimumDueMinor = summary.remainingMinimumDueMinor,
            remainingDueMinor = summary.remainingDueMinor,
            currencyCode = statement.currencyCode,
          )
        }
      }
    }

    if (!isStopped) scheduler.updateSchedule(userId, appendAfterCurrent = true)
    return Result.success()
  }

  private fun postReminder(
    statementId: String,
    issuer: String,
    last4: String,
    dueDate: Long,
    daysRemaining: Int,
    minimumDueMinor: Long,
    remainingDueMinor: Long,
    currencyCode: String,
  ) {
    runCatching {
      ensureNotificationChannel()
      val accountLabel = if (last4.isBlank()) issuer else "$issuer ••$last4"
      val timing = when (daysRemaining) {
        0 -> "Payment is due today"
        1 -> "Payment is due tomorrow"
        else -> "Payment is due in $daysRemaining days"
      }
      val minimumDue = formatMinor(minimumDueMinor, currencyCode)
      val remainingDue = formatMinor(remainingDueMinor, currencyCode)
      val intent = Intent(applicationContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_OPEN_CREDIT_CARDS, true)
      }
      val pendingIntent = PendingIntent.getActivity(
        applicationContext,
        statementId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
        .setContentTitle("SKYPLUS MONEY • Credit card payment")
        .setContentText("$accountLabel — $timing")
        .setStyle(
          NotificationCompat.BigTextStyle().bigText(
            "$accountLabel\n$timing\nDue: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDate))}\nMinimum due: $minimumDue $currencyCode\nRemaining: $remainingDue $currencyCode",
          ),
        )
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
      NotificationManagerCompat.from(applicationContext).notify(statementId.hashCode(), notification)
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = applicationContext.getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Credit Card Payment Reminders", NotificationManager.IMPORTANCE_DEFAULT)
          .apply { description = "Reminders before recorded credit card statement due dates." },
      )
    }
  }

  private fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val digits = runCatching { java.util.Currency.getInstance(currencyCode).defaultFractionDigits }
      .getOrDefault(2).coerceAtLeast(0)
    return java.math.BigDecimal.valueOf(amountMinor, digits).toPlainString()
  }

  companion object {
    const val KEY_USER_ID = "credit_card_reminder_user_id"
    const val CHANNEL_ID = "skyplus_credit_card_due_reminders"
    private const val MAX_LOOKAHEAD_DAYS = 30
  }
}
