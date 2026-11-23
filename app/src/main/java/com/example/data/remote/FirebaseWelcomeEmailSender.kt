package com.example.data.remote

import com.example.domain.repository.WelcomeEmailSender
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseWelcomeEmailSender @Inject constructor() : WelcomeEmailSender {
  override suspend fun sendWelcomeEmail(firstName: String) {
    require(firstName.isNotBlank() && firstName.length <= 60)
    FirebaseFunctions.getInstance()
      .getHttpsCallable("sendWelcomeEmail")
      .call(mapOf("firstName" to firstName.trim()))
      .await()
  }
}
