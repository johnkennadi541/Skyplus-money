package com.example.domain.repository

/** Outbound-only welcome email request; recipient is derived from the authenticated account. */
interface WelcomeEmailSender {
  suspend fun sendWelcomeEmail(firstName: String)
}
