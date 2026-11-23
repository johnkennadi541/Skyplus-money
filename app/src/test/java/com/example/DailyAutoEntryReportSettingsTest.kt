package com.example

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.data.local.ReportPreferencesDataSource
import com.example.domain.model.DailyAutoEntryReportSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyAutoEntryReportSettingsTest {

  @Test
  fun `daily report defaults to disabled at 9 PM local time`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val source = ReportPreferencesDataSource(context)
    val uid = "daily_report_default_test"

    val settings = source.observeDailyAutoEntrySettings(uid).first()

    assertFalse(settings.enabled)
    assertEquals(21, settings.hourOfDay)
    assertEquals(0, settings.minute)
    assertEquals("21:00", settings.formattedTime)
  }

  @Test
  fun `daily report enabled state and custom time persist per user`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val source = ReportPreferencesDataSource(context)
    val uid = "daily_report_custom_test"
    val selected = DailyAutoEntryReportSettings(enabled = true, hourOfDay = 6, minute = 35)

    source.setDailyAutoEntrySettings(uid, selected)
    val actual = source.observeDailyAutoEntrySettings(uid).first()

    assertTrue(actual.enabled)
    assertEquals(6, actual.hourOfDay)
    assertEquals(35, actual.minute)
    assertEquals("06:35", actual.formattedTime)
  }
}
