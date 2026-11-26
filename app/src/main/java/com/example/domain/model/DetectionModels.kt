package com.example.domain.model

import java.util.UUID

enum class DetectionConfidence {
  HIGH,
  MEDIUM,
  LOW,
}

enum class DetectionEventStatus {
  AUTO_SAVED,
  APPROVED,
  IN_REVIEW,
  DISMISSED,
  DUPLICATE,
}

data class TransactionDetectionEvent(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val rawPackageName: String,
  val senderOrTitle: String,
  val detectedAmountMinor: Long,
  val detectedCurrency: String,
  val detectedType: TransactionType,
  val detectedMerchant: String? = null,
  val detectedAccountLast4: String? = null,
  val referenceId: String? = null,
  val confidence: Float,
  val confidenceLevel: DetectionConfidence,
  val confidenceReasons: List<String> = emptyList(),
  val dedupFingerprint: String,
  val status: DetectionEventStatus,
  val mappedAccountId: String? = null,
  val createdTransactionId: String? = null,
  val receivedAt: Long = System.currentTimeMillis(),
) {
  val detectedMoney: Money
    get() = Money.fromMinorUnits(detectedAmountMinor, detectedCurrency)
}

data class AccountMappingRule(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val accountId: String,
  val matchPattern: String,
  val institutionHint: String? = null,
  val isAutoMapped: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

data class DetectionSettings(
  val userId: String,
  val isNotificationDetectionEnabled: Boolean = true,
  val isAutoSaveHighConfidenceEnabled: Boolean = true,
  val isSmsDetectionEnabled: Boolean = false,
  val isSmsPolicyRestricted: Boolean = true,
  val permittedPackages: Set<String> = DEFAULT_PERMITTED_PACKAGES,
) {
  companion object {
    val DEFAULT_PERMITTED_PACKAGES =
      setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "net.one97.paytm", // Paytm
        "com.phonepe.app", // PhonePe
        "in.org.npci.upiapp", // BHIM
        "com.amazon.mShop.android.shopping", // Amazon Pay
        "com.whatsapp", // WhatsApp Pay
        "com.snapwork.hdfc", // HDFC Bank
        "com.sbi.lotusintouch", // YONO SBI
        "com.icicibank.mobile", // iMobile ICICI
        "com.axis.mobile", // Axis Mobile
        "com.msf.kbank.mobile", // Kotak 811
        "com.chase.sig.android", // Chase Bank
        "com.wf.wellsfargomobile", // Wells Fargo
        "com.bofa.mobilebanking", // Bank of America
        "com.citi.citimobile", // Citi Mobile
        "com.alrajhibank", // Al Rajhi Bank
        "com.nbk.mobile", // National Bank of Kuwait
      )
  }
}

data class ParsedFinancialMessage(
  val amountMinor: Long,
  val currencyCode: String,
  val type: TransactionType,
  val status: TransactionStatus,
  val merchantOrPayee: String?,
  val accountLast4Hint: String?,
  val referenceId: String?,
  val confidenceScore: Float,
  val confidenceLevel: DetectionConfidence,
  val confidenceReasons: List<String>,
  val suggestedCategory: String? = null,
  val timestamp: Long = System.currentTimeMillis(),
)

data class AutoSavedTransactionNotice(
  val eventId: String,
  val transactionId: String,
  val accountName: String,
  val merchantOrPayee: String,
  val amountMinor: Long,
  val currencyCode: String,
  val type: TransactionType,
  val sourceApplication: String,
  val savedAt: Long = System.currentTimeMillis(),
  val userId: String = "",
) {
  val money: Money
    get() = Money.fromMinorUnits(amountMinor, currencyCode)
}
