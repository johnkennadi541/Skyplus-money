package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import com.example.domain.model.AuthSessionState
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.InvestmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class InvestmentMarketSyncWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val authRepository: AuthRepository,
  private val investmentRepository: InvestmentRepository,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val session = authRepository.observeAuthSession().first()
    val authenticatedUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (authenticatedUid.isNullOrBlank()) {
      return Result.success()
    }

    val settings = investmentRepository.observeSyncSettings(authenticatedUid).first()
    if (!settings.autoUpdateConsentEnabled) {
      return Result.success()
    }

    val outcome =
      investmentRepository.syncEligibleMarketPrices(
        authenticatedUserId = authenticatedUid,
        forceBypassCooldown = false,
      )

    return outcome.fold(
      onSuccess = { summary ->
        val retryAfterMillis = summary.retryAfterMillis
        if (retryAfterMillis != null && retryAfterMillis > 0L) {
          val delayedRetry =
            OneTimeWorkRequestBuilder<InvestmentMarketSyncWorker>()
              .setConstraints(
                Constraints.Builder()
                  .setRequiredNetworkType(
                    if (settings.syncOnlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
                  )
                  .setRequiresBatteryNotLow(true)
                  .build()
              )
              .setInitialDelay(retryAfterMillis, TimeUnit.MILLISECONDS)
              .build()
          try {
            WorkManager.getInstance(applicationContext).enqueue(delayedRetry)
            Result.success()
          } catch (_: Exception) {
            Result.retry()
          }
        } else if (summary.errorCount > 0) {
          // Retry network/response failures with WorkManager backoff when no Retry-After exists.
          Result.retry()
        } else {
          Result.success()
        }
      },
      onFailure = { Result.retry() },
    )
  }
}
