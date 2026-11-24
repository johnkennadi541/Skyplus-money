package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketPriceSyncScheduler @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  fun updateSchedule(autoUpdateConsentEnabled: Boolean, syncOnlyOnWifi: Boolean) {
    val workManager =
      try {
        WorkManager.getInstance(context)
      } catch (_: Exception) {
        return
      }
    if (!autoUpdateConsentEnabled) {
      workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
      return
    }

    val networkType = if (syncOnlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
    val constraints =
      Constraints.Builder()
        .setRequiredNetworkType(networkType)
        .setRequiresBatteryNotLow(true)
        .build()

    val request =
      PeriodicWorkRequestBuilder<InvestmentMarketSyncWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    workManager.enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  companion object {
    const val UNIQUE_WORK_NAME = "skyplus_investment_eod_nav_sync"
  }
}
