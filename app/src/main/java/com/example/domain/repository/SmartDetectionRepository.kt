package com.example.domain.repository

import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionSettings
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface SmartDetectionRepository {
  fun getDetectionEvents(userId: String): Flow<List<TransactionDetectionEvent>>
  fun getReviewQueue(userId: String): Flow<List<TransactionDetectionEvent>>
  fun observeLatestAutoSavedNotice(userId: String): Flow<AutoSavedTransactionNotice?>
  fun clearAutoSavedNotice()
  suspend fun getEventById(userId: String, eventId: String): TransactionDetectionEvent?
  suspend fun processIncomingEvent(
    userId: String,
    rawPackageName: String,
    senderOrTitle: String,
    parsed: ParsedFinancialMessage,
  ): Result<TransactionDetectionEvent>
  suspend fun approveReviewEvent(
    userId: String,
    eventId: String,
    selectedAccountId: String,
    category: String,
    merchant: String?,
    correctedType: TransactionType? = null,
    correctedAmountMinor: Long? = null,
  ): Result<Unit>
  suspend fun dismissReviewEvent(userId: String, eventId: String): Result<Unit>
  suspend fun clearDetectionHistory(userId: String): Result<Unit>

  // Account Mapping
  fun getAccountMappingRules(userId: String): Flow<List<AccountMappingRule>>
  suspend fun addAccountMappingRule(authenticatedUserId: String, rule: AccountMappingRule): Result<Unit>
  suspend fun deleteAccountMappingRule(userId: String, ruleId: String): Result<Unit>

  // Detection Settings
  fun getDetectionSettings(userId: String): Flow<DetectionSettings>
  suspend fun updateDetectionSettings(authenticatedUserId: String, settings: DetectionSettings): Result<Unit>
}
