package com.example.domain.usecase

import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionSettings
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionType
import com.example.domain.repository.SmartDetectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReviewQueueUseCase @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  operator fun invoke(userId: String): Flow<List<TransactionDetectionEvent>> =
    repository.getReviewQueue(userId)
}

class GetDetectionEventsUseCase @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  operator fun invoke(userId: String): Flow<List<TransactionDetectionEvent>> =
    repository.getDetectionEvents(userId)
}

class ProcessDetectionEventUseCase @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  suspend operator fun invoke(
    userId: String,
    rawPackageName: String,
    senderOrTitle: String,
    parsed: ParsedFinancialMessage,
  ): Result<TransactionDetectionEvent> =
    repository.processIncomingEvent(userId, rawPackageName, senderOrTitle, parsed)
}

class ApproveReviewItemUseCase @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  suspend operator fun invoke(
    userId: String,
    eventId: String,
    selectedAccountId: String,
    category: String,
    merchant: String?,
    correctedType: TransactionType? = null,
    correctedAmountMinor: Long? = null,
    rememberMerchantCategory: Boolean = false,
  ): Result<Unit> =
    repository.approveReviewEvent(
      userId = userId,
      eventId = eventId,
      selectedAccountId = selectedAccountId,
      category = category,
      merchant = merchant,
      correctedType = correctedType,
      correctedAmountMinor = correctedAmountMinor,
      rememberMerchantCategory = rememberMerchantCategory,
    )
}

class DismissReviewItemUseCase @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  suspend operator fun invoke(userId: String, eventId: String): Result<Unit> =
    repository.dismissReviewEvent(userId, eventId)
}

class DetectionSettingsUseCases @Inject constructor(
  private val repository: SmartDetectionRepository,
) {
  fun observeAutoSavedNotice(userId: String): Flow<AutoSavedTransactionNotice?> =
    repository.observeLatestAutoSavedNotice(userId)

  fun clearAutoSavedNotice() = repository.clearAutoSavedNotice()

  fun getSettings(userId: String): Flow<DetectionSettings> =
    repository.getDetectionSettings(userId)

  suspend fun updateSettings(
    authenticatedUserId: String,
    settings: DetectionSettings,
  ): Result<Unit> = repository.updateDetectionSettings(authenticatedUserId, settings)

  suspend fun getMerchantCategoryOverride(userId: String, merchant: String): String? =
    repository.getMerchantCategoryOverride(userId, merchant)

  fun getMappingRules(userId: String): Flow<List<AccountMappingRule>> =
    repository.getAccountMappingRules(userId)

  suspend fun addMappingRule(
    authenticatedUserId: String,
    rule: AccountMappingRule,
  ): Result<Unit> = repository.addAccountMappingRule(authenticatedUserId, rule)

  suspend fun deleteMappingRule(userId: String, ruleId: String): Result<Unit> =
    repository.deleteAccountMappingRule(userId, ruleId)

  suspend fun clearHistory(userId: String): Result<Unit> =
    repository.clearDetectionHistory(userId)
}
