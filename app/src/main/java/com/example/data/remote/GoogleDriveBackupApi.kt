package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.ResponseBody
import okio.Buffer
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
  private val maxBackupBytes: Int,
) {
  @Inject
  constructor(callFactory: Call.Factory) : this(callFactory, FILES_URL, UPLOAD_FILES_URL, MAX_BACKUP_BYTES)

  internal constructor(callFactory: Call.Factory, baseUrl: String) : this(
    callFactory,
    "${baseUrl.trimEnd('/')}/drive/v3/files",
    "${baseUrl.trimEnd('/')}/upload/drive/v3/files",
    MAX_BACKUP_BYTES,
  )

  internal constructor(callFactory: Call.Factory, baseUrl: String, maxBackupBytes: Int) : this(
    callFactory,
    "${baseUrl.trimEnd('/')}/drive/v3/files",
    "${baseUrl.trimEnd('/')}/upload/drive/v3/files",
    maxBackupBytes,
  )
  suspend fun uploadEncryptedBackup(accessToken: String, encryptedPayload: ByteArray): DriveBackupFile =
    withContext(Dispatchers.IO) {
      require(maxBackupBytes > 0) { "Backup size limit must be positive" }
      require(accessToken.isNotBlank()) { "Drive authorization is required" }
      require(encryptedPayload.isNotEmpty() && encryptedPayload.size <= maxBackupBytes) {
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
        if (!response.isSuccessful) throw IOException("Drive backup upload failed (${response.code})")
        val responseBody = readBodyUtf8Bounded(response.body, MAX_METADATA_RESPONSE_BYTES)
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
        val body = response.body ?: throw IOException("Drive returned an empty backup")
        val bytes = readBodyBytesBounded(body, maxBackupBytes.toLong())
        if (bytes.isEmpty()) {
          bytes.fill(0)
          throw IOException("Drive backup payload is empty")
        }
        bytes
      }
    }

  private fun findBackupFileBlocking(accessToken: String): DriveBackupFile? {
    val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
    val url = filesUrl.toHttpUrl().newBuilder()
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
      val json = JSONObject(readBodyUtf8Bounded(response.body, MAX_METADATA_RESPONSE_BYTES))
      val files = json.optJSONArray("files") ?: return@use null
      if (files.length() == 0) null else parseBackupFile(files.getJSONObject(0).toString())
    }
  }

  private fun readBodyUtf8Bounded(body: ResponseBody?, maxBytes: Long): String {
    val bytes = readBodyBytesBounded(body ?: throw IOException("Drive returned an empty response"), maxBytes)
    return try {
      bytes.toString(Charsets.UTF_8)
    } finally {
      bytes.fill(0)
    }
  }

  private fun readBodyBytesBounded(body: ResponseBody, maxBytes: Long): ByteArray {
    if (body.contentLength() > maxBytes) {
      throw IOException("Drive response exceeded the permitted size")
    }
    val buffer = Buffer()
    val source = body.source()
    var totalRead = 0L
    while (totalRead <= maxBytes) {
      val remaining = maxBytes + 1L - totalRead
      val read = source.read(buffer, minOf(8_192L, remaining))
      if (read == -1L) break
      totalRead += read
    }
    if (totalRead > maxBytes) {
      buffer.clear()
      throw IOException("Drive response exceeded the permitted size")
    }
    return buffer.readByteArray()
  }

  private fun parseBackupFile(json: String): DriveBackupFile? =
    try {
      val item = JSONObject(json)
      val id = item.getString("id").takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Drive file ID is missing")
      DriveBackupFile(
        id = id,
        name = item.optString("name", BACKUP_FILE_NAME),
        modifiedTime = item.optString("modifiedTime").takeIf(String::isNotBlank),
      )
    } catch (_: Exception) {
      null
    }

  private companion object {
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_FILES_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val BACKUP_FILE_NAME = "SKYPLUS MONEY Backup.spm"
    private const val BINARY_MIME_TYPE = "application/octet-stream"
    private const val JSON_MIME_TYPE = "application/json; charset=UTF-8"
    private const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
    private const val MAX_METADATA_RESPONSE_BYTES = 64L * 1024L
  }
}
