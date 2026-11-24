package com.example

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.data.local.ProfilePreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilePreferencesDataSourceTest {

  private val dataSource by lazy {
    ProfilePreferencesDataSource(ApplicationProvider.getApplicationContext<Context>())
  }

  @Test
  fun `Google display name seeds first name only when no user edit exists`() = runBlocking {
    val uid = "profile-seed-test-user"
    dataSource.ensureFirstName(uid, "Alex Morgan")
    assertEquals("Alex", dataSource.observeFirstName(uid).first())

    dataSource.saveFirstName(uid, "Alexander")
    dataSource.ensureFirstName(uid, "Different Google Name")
    assertEquals("Alexander", dataSource.observeFirstName(uid).first())
  }

  @Test
  fun `profile names are isolated by authenticated UID`() = runBlocking {
    dataSource.saveFirstName("profile-user-one", "Jamie")
    dataSource.saveFirstName("profile-user-two", "Taylor")

    assertEquals("Jamie", dataSource.observeFirstName("profile-user-one").first())
    assertEquals("Taylor", dataSource.observeFirstName("profile-user-two").first())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `blank profile name is rejected`() = runBlocking {
    dataSource.saveFirstName("profile-validation-user", "   ")
  }
}
