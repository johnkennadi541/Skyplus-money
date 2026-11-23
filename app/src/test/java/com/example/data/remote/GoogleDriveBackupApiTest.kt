package com.example.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveBackupApiTest {

  @Test
  fun `upload creates one named encrypted backup using app file scope token`() = runBlocking {
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("""{"files":[]}"""))
      server.enqueue(
        MockResponse().setBody(
          """{"id":"backup-file-1","name":"SKYPLUS MONEY Backup.spm","modifiedTime":"2026-11-23T18:00:00.000Z"}"""
        )
      )
      val api = GoogleDriveBackupApi(OkHttpClient(), server.url("/").toString())
      val payload = byteArrayOf(4, 8, 15, 16, 23, 42)

      val saved = api.uploadEncryptedBackup("test-access-token", payload)
      val lookup = server.takeRequest(2, TimeUnit.SECONDS)!!
      val upload = server.takeRequest(2, TimeUnit.SECONDS)!!

      assertEquals("GET", lookup.method)
      assertEquals("Bearer test-access-token", lookup.getHeader("Authorization"))
      assertTrue(lookup.requestUrl!!.queryParameter("q").orEmpty().contains("SKYPLUS MONEY Backup.spm"))
      assertEquals("POST", upload.method)
      assertTrue(upload.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
      assertTrue(upload.body.readUtf8().contains("SKYPLUS MONEY Backup.spm"))
      assertEquals("backup-file-1", saved.id)
      assertEquals("SKYPLUS MONEY Backup.spm", saved.name)
    }
  }

  @Test
  fun `download retrieves only the app backup file bytes`() = runBlocking {
    MockWebServer().use { server ->
      server.enqueue(
        MockResponse().setBody(
          """{"files":[{"id":"backup-file-2","name":"SKYPLUS MONEY Backup.spm","modifiedTime":"2026-11-23T18:00:00.000Z"}]}"""
        )
      )
      val payload = byteArrayOf(0, 1, 2, 3, 127, -1)
      server.enqueue(MockResponse().setBody(Buffer().write(payload)))

      val api = GoogleDriveBackupApi(OkHttpClient(), server.url("/").toString())
      val downloaded = api.downloadEncryptedBackup("test-access-token")
      val lookup = server.takeRequest(2, TimeUnit.SECONDS)!!
      val download = server.takeRequest(2, TimeUnit.SECONDS)!!

      assertEquals("GET", lookup.method)
      assertEquals("Bearer test-access-token", lookup.getHeader("Authorization"))
      assertEquals("GET", download.method)
      assertEquals("backup-file-2", download.requestUrl!!.pathSegments.last())
      assertEquals("media", download.requestUrl!!.queryParameter("alt"))
      assertArrayEquals(payload, downloaded)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Drive API refuses empty access tokens`() = runBlocking {
    GoogleDriveBackupApi(OkHttpClient(), "http://localhost")
      .downloadEncryptedBackup("")
  }
}
