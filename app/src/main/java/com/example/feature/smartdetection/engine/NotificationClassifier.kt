package com.example.feature.smartdetection.engine

import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType

sealed interface NotificationClassification {
  data object Sensitive : NotificationClassification

  data object NotFinancial : NotificationClassification

  data class FinancialCandidate(
    val transaction: ParsedFinancialMessage,
  ) : NotificationClassification

  data class NeedsReview(
    val transaction: ParsedFinancialMessage,
  ) : NotificationClassification
}

/**
 * Pure classification boundary. This class deliberately has no repository, DAO, Context,
 * persistence, or auto-save dependency.
 */
object NotificationClassifier {
  fun classify(
    content: String,
    packageName: String,
    timestamp: Long,
  ): NotificationClassification {
    if (SensitiveNotificationFilter.containsSensitiveContent(content)) {
      return NotificationClassification.Sensitive
    }

    val parsed =
      SmartTransactionParser.parse(
        text = content,
        packageNameHint = packageName,
        timestamp = timestamp,
      ) ?: return NotificationClassification.NotFinancial

    val safeForCandidate =
      parsed.status == TransactionStatus.COMPLETED &&
        parsed.confidenceLevel.name == "HIGH" &&
        parsed.type != TransactionType.PENDING &&
        parsed.type != TransactionType.FAILED &&
        parsed.type != TransactionType.TRANSFER

    return if (safeForCandidate) {
      NotificationClassification.FinancialCandidate(parsed)
    } else {
      NotificationClassification.NeedsReview(parsed)
    }
  }
}
