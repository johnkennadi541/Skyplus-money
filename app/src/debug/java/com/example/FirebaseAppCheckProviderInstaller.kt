package com.example

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-only App Check provider. Debug tokens are emitted by the SDK at runtime and must
 * be registered privately in Firebase Console; they are never stored in source control.
 */
internal object FirebaseAppCheckProviderInstaller {
  fun install() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
      DebugAppCheckProviderFactory.getInstance(),
    )
  }
}
