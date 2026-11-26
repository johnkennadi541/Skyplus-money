package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType

@Entity(
  tableName = "transaction_detection_events",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "status"]),
    Index(value = ["userId", "dedupFingerprint"]),
    Index(value = ["userId", "referenceId"]),
    Index(value = ["userId", "receivedAt"]),
  ],
)
data class TransactionDetectionEventEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val rawPackageName: String,
  val senderOrTitle: String,
  val detectedAmountMinor: Long,
  val detectedCurrency: String,
  val detectedType: String,
  val detectedMerchant: String?,
  val detectedAccountLast4: String?,
  val referenceId: String?,
  val confidence: Float,
  val confidenceLevel: String,
  val confidenceReasonsJoined: String,
  val dedupFingerprint: String,
  val status: String,
  val mappedAccountId: String?,
  val createdTransactionId: String?,
  val receivedAt: Long,
) {
  fun toDomain(): TransactionDetectionEvent =
    TransactionDetectionEvent(
      id = id,
      userId = userId,
      rawPackageName = rawPackageName,
      senderOrTitle = senderOrTitle,
      detectedAmountMinor = detectedAmountMinor,
      detectedCurrency = detectedCurrency,
      detectedType = runCatching { TransactionType.valueOf(detectedType) }.getOrDefault(TransactionType.EXPENSE),
      detectedMerchant = detectedMerchant,
      detectedAccountLast4 = detectedAccountLast4,
      referenceId = referenceId,
      confidence = confidence,
      confidenceLevel = runCatching { DetectionConfidence.valueOf(confidenceLevel) }.getOrDefault(DetectionConfidence.MEDIUM),
      confidenceReasons = if (confidenceReasonsJoined.isBlank()) emptyList() else confidenceReasonsJoined.split(";;;"),
      dedupFingerprint = dedupFingerprint,
      status = runCatching { DetectionEventStatus.valueOf(status) }.getOrDefault(DetectionEventStatus.IN_REVIEW),
      mappedAccountId = mappedAccountId,
      createdTransactionId = createdTransactionId,
      receivedAt = receivedAt,
    )

  companion object {
    fun fromDomain(event: TransactionDetectionEvent): TransactionDetectionEventEntity =
      TransactionDetectionEventEntity(
        id = event.id,
        userId = event.userId,
        rawPackageName = event.rawPackageName,
        senderOrTitle = event.senderOrTitle,
        detectedAmountMinor = event.detectedAmountMinor,
        detectedCurrency = event.detectedCurrency,
        detectedType = event.detectedType.name,
        detectedMerchant = event.detectedMerchant,
        detectedAccountLast4 = event.detectedAccountLast4,
        referenceId = event.referenceId,
        confidence = event.confidence,
        confidenceLevel = event.confidenceLevel.name,
        confidenceReasonsJoined = event.confidenceReasons.joinToString(";;;"),
        dedupFingerprint = event.dedupFingerprint,
        status = event.status.name,
        mappedAccountId = event.mappedAccountId,
        createdTransactionId = event.createdTransactionId,
        receivedAt = event.receivedAt,
      )
  }
}
