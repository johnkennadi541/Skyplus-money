package com.example

import com.example.domain.model.ConsentRecord
import com.example.domain.repository.ConsentRepository
import com.example.domain.usecase.CheckUserConsentUseCase
import com.example.domain.usecase.SaveUserConsentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentLogicUnitTest {

  private class InMemoryConsentRepository : ConsentRepository {
    val storage = mutableMapOf<String, ConsentRecord>()

    override suspend fun getConsent(uid: String): ConsentRecord? = storage[uid]

    override suspend fun saveConsent(uid: String, termsVersion: String, privacyVersion: String) {
      storage[uid] = ConsentRecord(termsVersion, privacyVersion, System.currentTimeMillis())
    }

    override suspend fun clearConsent(uid: String) {
      storage.remove(uid)
    }
  }

  private lateinit var repository: InMemoryConsentRepository
  private lateinit var checkConsentUseCase: CheckUserConsentUseCase
  private lateinit var saveConsentUseCase: SaveUserConsentUseCase

  @Before
  fun setUp() {
    repository = InMemoryConsentRepository()
    checkConsentUseCase = CheckUserConsentUseCase(repository)
    saveConsentUseCase = SaveUserConsentUseCase(repository)
  }

  @Test
  fun `first-time user has no consent record and is detected as missing consent`() = runTest {
    val record = checkConsentUseCase("user_101")
    assertNull(record)
  }

  @Test
  fun `saving consent marks record as up to date with current versions`() = runTest {
    saveConsentUseCase("user_101", ConsentRecord.CURRENT_TERMS_VERSION, ConsentRecord.CURRENT_PRIVACY_VERSION)
    val record = checkConsentUseCase("user_101")
    assertNotNull(record)
    assertTrue(record!!.isUpToDate)
    assertEquals(ConsentRecord.CURRENT_TERMS_VERSION, record.termsVersion)
    assertEquals(ConsentRecord.CURRENT_PRIVACY_VERSION, record.privacyVersion)
  }

  @Test
  fun `changed terms version invalidates consent and requires re-consent`() = runTest {
    // User consented to old terms version
    repository.saveConsent("user_101", "0.9.0", ConsentRecord.CURRENT_PRIVACY_VERSION)
    val record = checkConsentUseCase("user_101")
    assertNotNull(record)
    assertFalse(record!!.isUpToDate)
  }

  @Test
  fun `changed privacy policy version invalidates consent and requires re-consent`() = runTest {
    // User consented to old privacy version
    repository.saveConsent("user_101", ConsentRecord.CURRENT_TERMS_VERSION, "0.9.5")
    val record = checkConsentUseCase("user_101")
    assertNotNull(record)
    assertFalse(record!!.isUpToDate)
  }

  @Test
  fun `consent is strictly isolated per UID and does not transfer between users`() = runTest {
    saveConsentUseCase("user_A", ConsentRecord.CURRENT_TERMS_VERSION, ConsentRecord.CURRENT_PRIVACY_VERSION)
    val userARecord = checkConsentUseCase("user_A")
    val userBRecord = checkConsentUseCase("user_B")

    assertNotNull(userARecord)
    assertTrue(userARecord!!.isUpToDate)
    assertNull(userBRecord)
  }
}
