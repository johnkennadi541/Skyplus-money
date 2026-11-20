package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.TransactionDetectionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDetectionEventDao {
  @Query("SELECT * FROM transaction_detection_events WHERE userId = :userId ORDER BY receivedAt DESC")
  fun getAllEvents(userId: String): Flow<List<TransactionDetectionEventEntity>>

  @Query(
    "SELECT * FROM transaction_detection_events WHERE userId = :userId AND status = 'IN_REVIEW' ORDER BY receivedAt DESC"
  )
  fun getReviewQueue(userId: String): Flow<List<TransactionDetectionEventEntity>>

  @Query(
    "SELECT * FROM transaction_detection_events WHERE userId = :userId AND id = :eventId LIMIT 1"
  )
  suspend fun getEventById(userId: String, eventId: String): TransactionDetectionEventEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEvent(event: TransactionDetectionEventEntity)

  @Query(
    """
    UPDATE transaction_detection_events
    SET rawPackageName = :rawPackageName,
        senderOrTitle = :senderOrTitle,
        detectedAmountMinor = :detectedAmountMinor,
        detectedCurrency = :detectedCurrency,
        detectedType = :detectedType,
        detectedMerchant = :detectedMerchant,
        detectedAccountLast4 = :detectedAccountLast4,
        referenceId = :referenceId,
        confidence = :confidence,
        confidenceLevel = :confidenceLevel,
        confidenceReasonsJoined = :confidenceReasonsJoined,
        dedupFingerprint = :dedupFingerprint,
        status = :status,
        mappedAccountId = :mappedAccountId,
        createdTransactionId = :createdTransactionId,
        receivedAt = :receivedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateEventScoped(
    id: String,
    userId: String,
    rawPackageName: String,
    senderOrTitle: String,
    detectedAmountMinor: Long,
    detectedCurrency: String,
    detectedType: String,
    detectedMerchant: String?,
    detectedAccountLast4: String?,
    referenceId: String?,
    confidence: Float,
    confidenceLevel: String,
    confidenceReasonsJoined: String,
    dedupFingerprint: String,
    status: String,
    mappedAccountId: String?,
    createdTransactionId: String?,
    receivedAt: Long,
  ): Int

  suspend fun updateEvent(
    event: TransactionDetectionEventEntity,
    authenticatedUserId: String,
  ): Int {
    if (authenticatedUserId.isBlank() || event.userId != authenticatedUserId) return 0
    return updateEventScoped(
      id = event.id,
      userId = authenticatedUserId,
      rawPackageName = event.rawPackageName,
      senderOrTitle = event.senderOrTitle,
      detectedAmountMinor = event.detectedAmountMinor,
      detectedCurrency = event.detectedCurrency,
      detectedType = event.detectedType,
      detectedMerchant = event.detectedMerchant,
      detectedAccountLast4 = event.detectedAccountLast4,
      referenceId = event.referenceId,
      confidence = event.confidence,
      confidenceLevel = event.confidenceLevel,
      confidenceReasonsJoined = event.confidenceReasonsJoined,
      dedupFingerprint = event.dedupFingerprint,
      status = event.status,
      mappedAccountId = event.mappedAccountId,
      createdTransactionId = event.createdTransactionId,
      receivedAt = event.receivedAt,
    )
  }

  @Query(
    "SELECT * FROM transaction_detection_events WHERE userId = :userId AND dedupFingerprint = :fingerprint LIMIT 1"
  )
  suspend fun findByFingerprint(userId: String, fingerprint: String): TransactionDetectionEventEntity?

  @Query(
    "SELECT * FROM transaction_detection_events WHERE userId = :userId AND dedupFingerprint = :fingerprint"
  )
  suspend fun findAllByFingerprint(userId: String, fingerprint: String): List<TransactionDetectionEventEntity>

  @Query(
    "SELECT * FROM transaction_detection_events WHERE userId = :userId AND referenceId = :referenceId LIMIT 1"
  )
  suspend fun findByReferenceId(userId: String, referenceId: String): TransactionDetectionEventEntity?

  @Query("DELETE FROM transaction_detection_events WHERE userId = :userId")
  suspend fun clearAllEvents(userId: String)
}
