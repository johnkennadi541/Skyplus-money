package com.example

import android.app.Notification
import com.example.service.NotificationCapturePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {
  @Test
  fun `allows ordinary posted notifications`() {
    assertTrue(NotificationCapturePolicy.shouldCapture(false, 0))
  }

  @Test
  fun `ignores ongoing notifications`() {
    assertFalse(NotificationCapturePolicy.shouldCapture(true, 0))
    assertFalse(
      NotificationCapturePolicy.shouldCapture(
        false,
        Notification.FLAG_ONGOING_EVENT,
      ),
    )
  }

  @Test
  fun `ignores group summary notifications`() {
    assertFalse(
      NotificationCapturePolicy.shouldCapture(
        false,
        Notification.FLAG_GROUP_SUMMARY,
      ),
    )
  }

  @Test
  fun `ignores notifications that are both ongoing and group summaries`() {
    assertFalse(
      NotificationCapturePolicy.shouldCapture(
        true,
        Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY,
      ),
    )
  }
}
