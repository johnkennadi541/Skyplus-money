package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        // Per-holding network/response failures are summarized inside a successful
        // repository result; ask WorkManager to retry those rather than waiting a day.
        if (summary.errorCount > 0) Result.retry() else Result.success()
      },
      onFailure = { Result.retry() },
    )
  }
}
