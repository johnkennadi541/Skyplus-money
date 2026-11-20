package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for SKYPLUS MONEY.
 * Annotated with [HiltAndroidApp] and provides [HiltWorkerFactory] for WorkManager tasks.
 */
@HiltAndroidApp
class SkyplusMoneyApp : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
