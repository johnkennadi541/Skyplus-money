package com.example.data.remote

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests the narrow Drive file scope only when the user enables backup.
 * Credential Manager Google sign-in establishes the default account first.
 */
@Singleton
class GoogleDriveAuthorizationClient @Inject constructor() {

  fun authorize(activity: Activity): Task<AuthorizationResult> {
    val request =
      AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()
    return Identity.getAuthorizationClient(activity).authorize(request)
  }

  fun resultFromIntent(activity: Activity, data: Intent): AuthorizationResult =
    Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

  companion object {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
  }
}
