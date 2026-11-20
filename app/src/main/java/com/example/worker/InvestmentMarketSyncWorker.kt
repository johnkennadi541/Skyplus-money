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
    val settings = investmentRepository.observeSyncSettings().first()
    if (!settings.autoUpdateConsentEnabled) {
      return Result.success()
    }

    val session = authRepository.observeAuthSession().first()
    val authenticatedUid = (session as? AuthSessionState.Authenticated)?.user?.uid
    if (authenticatedUid.isNullOrBlank()) {
      return Result.success()
    }

    val outcome =
      investmentRepository.syncEligibleMarketPrices(
        authenticatedUserId = authenticatedUid,
        allowOverwriteManual = false,
        forceBypassCooldown = false,
      )

    return if (outcome.isSuccess) Result.success() else Result.retry()
  }
}
