package com.example

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release-only App Check provider. Production builds cannot select the debug provider.
 */
internal object FirebaseAppCheckProviderInstaller {
  fun install() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
      PlayIntegrityAppCheckProviderFactory.getInstance(),
    )
  }
}
