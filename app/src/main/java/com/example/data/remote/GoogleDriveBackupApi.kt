package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class DriveBackupFile(
  val id: String,
  val name: String,
  val modifiedTime: String?,
)

/**
 * Minimal Google Drive REST client for the app-created backup file only.
 * It never lists or downloads unrelated Drive documents.
 */
@Singleton
class GoogleDriveBackupApi private constructor(
  private val callFactory: Call.Factory,
  private val filesUrl: String,
  private val uploadFilesUrl: String,
) {
  @Inject
  constructor(callFactory: Call.Factory) : this(callFactory, FILES_URL, UPLOAD_FILES_URL)

  internal constructor(callFactory: Call.Factory, baseUrl: String) : this(
    callFactory,
    "${baseUrl.trimEnd('/')}/drive/v3/files",
    "${baseUrl.trimEnd('/')}/upload/drive/v3/files",
  )
  suspend fun uploadEncryptedBackup(accessToken: String, encryptedPayload: ByteArray): DriveBackupFile =
    withContext(Dispatchers.IO) {
      require(accessToken.isNotBlank()) { "Drive authorization is required" }
      require(encryptedPayload.isNotEmpty() && encryptedPayload.size <= MAX_BACKUP_BYTES) {
        "Backup payload is empty or exceeds the supported size"
      }

      val existing = findBackupFileBlocking(accessToken)
      val url = uploadFilesUrl.toHttpUrl().newBuilder()
        .apply {
          if (existing != null) addPathSegment(existing.id)
          addQueryParameter("uploadType", if (existing == null) "multipart" else "media")
          addQueryParameter("fields", "id,name,modifiedTime")
        }
        .build()

      val body =
        if (existing == null) {
          val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("mimeType", BINARY_MIME_TYPE)
            .toString()
            .toRequestBody(JSON_MIME_TYPE.toMediaType())
          MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata)
            .addPart(encryptedPayload.toRequestBody(BINARY_MIME_TYPE.toMediaType()))
            .build()
        } else {
          encryptedPayload.toRequestBody(BINARY_MIME_TYPE.toMediaType())
        }

      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")
        .apply { if (existing == null) post(body) else patch(body) }
        .build()

      callFactory.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("Drive backup upload failed (${response.code})")
        parseBackupFile(responseBody)
          ?: throw IOException("Drive returned an invalid backup file response")
      }
    }

  suspend fun downloadEncryptedBackup(accessToken: String): ByteArray? =
    withContext(Dispatchers.IO) {
      require(accessToken.isNotBlank()) { "Drive authorization is required" }
      val backup = findBackupFileBlocking(accessToken) ?: return@withContext null
      val url = filesUrl.toHttpUrl().newBuilder()
        .addPathSegment(backup.id)
        .addQueryParameter("alt", "media")
        .build()
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()

      callFactory.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Drive backup download failed (${response.code})")
        val bytes = response.body?.bytes() ?: throw IOException("Drive returned an empty backup")
        if (bytes.isEmpty() || bytes.size > MAX_BACKUP_BYTES) {
          throw IOException("Drive backup payload is empty or exceeds the supported size")
        }
        bytes
      }
    }

  private fun findBackupFileBlocking(accessToken: String): DriveBackupFile? {
    val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
    val url = FILES_URL.toHttpUrl().newBuilder()
      .addQueryParameter("q", query)
      .addQueryParameter("pageSize", "10")
      .addQueryParameter("orderBy", "modifiedTime desc")
      .addQueryParameter("fields", "files(id,name,modifiedTime)")
      .build()
    val request = Request.Builder()
      .url(url)
      .header("Authorization", "Bearer $accessToken")
      .get()
      .build()

    return callFactory.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("Drive backup lookup failed (${response.code})")
      val json = JSONObject(response.body?.string().orEmpty())
      val files = json.optJSONArray("files") ?: return@use null
      if (files.length() == 0) null else parseBackupFile(files.getJSONObject(0).toString())
    }
  }

  private fun parseBackupFile(json: String): DriveBackupFile? = runCatching {
    val item = JSONObject(json)
    val id = item.getString("id").takeIf(String::isNotBlank) ?: return null
    DriveBackupFile(
      id = id,
      name = item.optString("name", BACKUP_FILE_NAME),
      modifiedTime = item.optString("modifiedTime").takeIf(String::isNotBlank),
    )
  }.getOrNull()

  private companion object {
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_FILES_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val BACKUP_FILE_NAME = "SKYPLUS MONEY Backup.spm"
    private const val BINARY_MIME_TYPE = "application/octet-stream"
    private const val JSON_MIME_TYPE = "application/json; charset=UTF-8"
    private const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
  }
}
