package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.DefaultSecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `verify package id app name and security manifest flags`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("com.skyplus.money", context.packageName)
    assertEquals("SkyPlus Money", context.getString(R.string.app_name))
    assertEquals("Take Control. Build Wealth.", context.getString(R.string.app_tagline))

    val appInfo = context.applicationInfo
    assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    assertTrue(context.applicationContext is SkyplusMoneyApp)
  }

  @Test
  fun `verify security baseline prohibits guest mode and unencrypted backup`() {
    assertTrue(DefaultSecurityPolicy.requiresMandatoryAuthentication)
    assertFalse(DefaultSecurityPolicy.allowsGuestMode)
    assertFalse(DefaultSecurityPolicy.allowsUnencryptedLocalBackup)
    assertTrue(DefaultSecurityPolicy.enforcesKeystoreKeyProtection)
  }
}
