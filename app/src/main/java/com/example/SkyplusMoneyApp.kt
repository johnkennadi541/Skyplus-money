package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for SKYPLUS MONEY.
 * Annotated with [HiltAndroidApp] and provides [HiltWorkerFactory] for WorkManager tasks.
 */
@HiltAndroidApp
class SkyplusMoneyApp : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory

  override fun onCreate() {
    super.onCreate()
    // Install App Check before any app code obtains Firebase Auth or Functions instances.
    checkNotNull(FirebaseApp.initializeApp(this)) {
      "Default Firebase app could not be initialized. Check google-services.json."
    }
    FirebaseAppCheckProviderInstaller.install()
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
