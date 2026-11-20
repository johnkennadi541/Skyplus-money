package com.example.feature.family

/**
 * Family Finance module contract (Phase 6).
 * Enforces equal Co-Admin permissions at both Firestore Security Rules and UI layers,
 * with FCM notifications excluding the actor who performed the change.
 */
object FamilyContract {
  const val TARGET_PHASE: Int = 6
  const val CO_ADMIN_ROLE: String = "CO_ADMIN"
  const val EXCLUDE_ACTOR_FROM_OWN_NOTIFICATIONS: Boolean = true
}
