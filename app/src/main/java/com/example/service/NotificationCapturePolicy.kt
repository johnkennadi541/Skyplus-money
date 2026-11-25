package com.example.service

import android.app.Notification

/**
 * Metadata-only filtering performed before notification extras are inspected.
 * SMS-only source validation remains enforced separately by SmsNotificationPrivacyGate.
 */
internal object NotificationCapturePolicy {
  fun shouldCapture(isOngoing: Boolean, notificationFlags: Int): Boolean {
    if (isOngoing) return false
    if (notificationFlags and Notification.FLAG_ONGOING_EVENT != 0) return false
    if (notificationFlags and Notification.FLAG_GROUP_SUMMARY != 0) return false
    return true
  }
}
