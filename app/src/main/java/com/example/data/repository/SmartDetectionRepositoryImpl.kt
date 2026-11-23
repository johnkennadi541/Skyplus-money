package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.AccountMappingRuleDao
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.dao.TransactionDetectionEventDao
import com.example.data.local.db.entity.AccountMappingRuleEntity
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.data.local.db.entity.TransactionDetectionEventEntity
import com.example.domain.model.AccountMappingRule
import com.example.domain.model.AutoSavedTransactionNotice
import com.example.domain.model.DetectionConfidence
import com.example.domain.model.DetectionEventStatus
import com.example.domain.model.DetectionSettings
import com.example.domain.model.FinancialTransaction
import com.example.domain.model.ParsedFinancialMessage
import com.example.domain.model.TransactionDetectionEvent
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.domain.repository.FinancialTransactionRepository
import com.example.domain.repository.SmartDetectionRepository
import com.example.feature.smartdetection.engine.DeduplicationEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private val Context.detectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "skyplus_detection_settings")

@Singleton
class SmartDetectionRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context,
  private val database: AppDatabase,
  private val eventDao: TransactionDetectionEventDao,
  private val mappingDao: AccountMappingRuleDao,
  private val accountDao: FinancialAccountDao,
  private val transactionDao: FinancialTransactionDao,
  private val transactionRepository: FinancialTransactionRepository,
  private val dispatchers: DispatcherProvider,
) : SmartDetectionRepository {

  private val _latestAutoSavedNotice = MutableStateFlow<AutoSavedTransactionNotice?>(null)

  override fun observeLatestAutoSavedNotice(userId: String): Flow<AutoSavedTransactionNotice?> =
    _latestAutoSavedNotice.map { notice ->
      if (notice != null && notice.userId == userId) notice else null
    }

  override fun clearAutoSavedNotice() {
    _latestAutoSavedNotice.value = null
  }

  override fun getDetectionEvents(userId: String): Flow<List<TransactionDetectionEvent>> =
    eventDao.getAllEvents(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getReviewQueue(userId: String): Flow<List<TransactionDetectionEvent>> =
    eventDao.getReviewQueue(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun getEventById(userId: String, eventId: String): TransactionDetectionEvent? =
    withContext(dispatchers.io) {
      eventDao.getEventById(userId, eventId)?.toDomain()
    }

  override suspend fun processIncomingEvent(
    userId: String,
    rawPackageName: String,
    senderOrTitle: String,
    parsed: ParsedFinancialMessage,
  ): Result<TransactionDetectionEvent> =
    withContext(dispatchers.io) {
      runCatchingPreservingCancellation {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        val settings = getDetectionSettings(userId).first()
        if (!settings.isNotificationDetectionEnabled) {
          throw IllegalStateException("Smart notification detection is disabled in settings")
        }

        val safeTitle = sanitizeSenderTitle(senderOrTitle)
        val safeMerchant = sanitizeMerchant(parsed.merchantOrPayee)

        var pendingNotice: com.example.domain.model.AutoSavedTransactionNotice? = null
        val processedEvent = database.withTransaction {
          // 1. Resolve target account upfront so deduplication/ambiguity checks respect distinct accounts
          val accounts = accountDao.getActiveAccountsSync(userId)
          val accountsById = accounts.associateBy { it.id }
          var resolvedAccountId: String? = null
          var resolvedAccountName: String? = null

          if (!parsed.accountLast4Hint.isNullOrBlank()) {
            val rule = mappingDao.findRuleForPattern(userId, parsed.accountLast4Hint)
            val matchedAcc = rule?.let { r -> accounts.firstOrNull { it.id == r.accountId } }
            if (matchedAcc != null) {
              resolvedAccountId = matchedAcc.id
              resolvedAccountName = matchedAcc.name
            }
          }

          if (resolvedAccountId == null && !parsed.accountLast4Hint.isNullOrBlank()) {
            val matchingAccounts = accounts.filter { it.accountNumberLast4 == parsed.accountLast4Hint }
            // A repeated last-four value across multiple institutions is ambiguous.
            // Never choose the first matching account by list order.
            if (matchingAccounts.size == 1) {
              resolvedAccountId = matchingAccounts.single().id
              resolvedAccountName = matchingAccounts.single().name
            }
          }

          // Never guess an account from currency alone. A single same-currency
          // account is not proof that the bank SMS belongs to that account.
          // Unmatched account references remain in the exception/review queue.

          val fingerprint = DeduplicationEngine.generateFingerprint(userId, parsed)

          // 2. Deterministic Deduplication: Reference ID across events and saved transactions
          if (!parsed.referenceId.isNullOrBlank()) {
            val existingByRef = eventDao.findByReferenceId(userId, parsed.referenceId)
            if (existingByRef != null) {
              return@withTransaction existingByRef.toDomain().copy(status = DetectionEventStatus.DUPLICATE)
            }
            val existingTxnByRef = transactionDao.findByReferenceId(userId, parsed.referenceId)
            if (existingTxnByRef != null) {
              val dupEvent =
                TransactionDetectionEventEntity(
                  id = UUID.randomUUID().toString(),
                  userId = userId,
                  rawPackageName = rawPackageName,
                  senderOrTitle = safeTitle,
                  detectedAmountMinor = parsed.amountMinor,
                  detectedCurrency = parsed.currencyCode,
                  detectedType = parsed.type.name,
                  detectedMerchant = safeMerchant,
                  detectedAccountLast4 = parsed.accountLast4Hint,
                  referenceId = parsed.referenceId,
                  confidence = parsed.confidenceScore,
                  confidenceLevel = parsed.confidenceLevel.name,
                  confidenceReasonsJoined = parsed.confidenceReasons.joinToString(";;;"),
                  dedupFingerprint = fingerprint,
                  status = DetectionEventStatus.DUPLICATE.name,
                  mappedAccountId = existingTxnByRef.accountId,
                  createdTransactionId = existingTxnByRef.id,
                  receivedAt = parsed.timestamp,
                )
              eventDao.insertEvent(dupEvent)
              return@withTransaction dupEvent.toDomain()
            }

            val existingByFingerprint = eventDao.findByFingerprint(userId, fingerprint)
            if (existingByFingerprint != null) {
              return@withTransaction existingByFingerprint.toDomain().copy(status = DetectionEventStatus.DUPLICATE)
            }
          }

          // 3. Exact OS re-delivery deduplication for NOREF alerts (same package & <= 1s timestamp delta)
          val sameBucketEvents =
            if (parsed.referenceId.isNullOrBlank()) {
              eventDao.findAllByFingerprint(userId, fingerprint)
            } else {
              emptyList()
            }

          if (parsed.referenceId.isNullOrBlank()) {
            val exactRedelivery =
              sameBucketEvents.firstOrNull { existing ->
                existing.rawPackageName == rawPackageName &&
                  abs(existing.receivedAt - parsed.timestamp) <= 1_000L
              }
            if (exactRedelivery != null) {
              return@withTransaction exactRedelivery.toDomain().copy(status = DetectionEventStatus.DUPLICATE)
            }
          }

          // 4. 10-Minute Ambiguity Detection (NO null-merchant wildcard; respects distinct Ref IDs & accounts).
          // Ambiguous repeated transactions go to IN_REVIEW rather than being silently discarded as DUPLICATE.
          val timeWindowMs = 10 * 60 * 1000L
          val recentTxns =
            transactionDao.findRecentMatching(
              userId = userId,
              amountMinor = parsed.amountMinor,
              currencyCode = parsed.currencyCode,
              startTime = parsed.timestamp - timeWindowMs,
              endTime = parsed.timestamp + timeWindowMs,
            )

          val matchesRecentSavedTxn =
            if (safeMerchant.isNullOrBlank()) {
              false // Never wildcard-match when incoming merchant is null/blank
            } else {
              recentTxns.any { candidate ->
                val sameType = candidate.type == parsed.type.name
                val distinctRefIds =
                  !parsed.referenceId.isNullOrBlank() &&
                    !candidate.referenceId.isNullOrBlank() &&
                    !parsed.referenceId.equals(candidate.referenceId, ignoreCase = true)
                val sameOrCompatibleAccount =
                  when {
                    resolvedAccountId != null -> candidate.accountId == resolvedAccountId
                    !parsed.accountLast4Hint.isNullOrBlank() -> {
                      val candAccLast4 = accountsById[candidate.accountId]?.accountNumberLast4
                      candAccLast4 == null || candAccLast4 == parsed.accountLast4Hint
                    }
                    else -> true
                  }
                val candMerchant = candidate.merchantOrPayee?.trim()
                val merchantsMatch =
                  !candMerchant.isNullOrBlank() &&
                    merchantsEquivalent(safeMerchant, candMerchant)

                sameType && !distinctRefIds && sameOrCompatibleAccount && merchantsMatch
              }
            }

          val matchesRecentPendingEvent =
            if (safeMerchant.isNullOrBlank()) {
              false
            } else {
              sameBucketEvents.any { existing ->
                val distinctRefIds =
                  !parsed.referenceId.isNullOrBlank() &&
                    !existing.referenceId.isNullOrBlank() &&
                    !parsed.referenceId.equals(existing.referenceId, ignoreCase = true)
                val sameOrCompatibleAccount =
                  resolvedAccountId == null ||
                    existing.mappedAccountId == null ||
                    existing.mappedAccountId == resolvedAccountId
                val existingMerchant = existing.detectedMerchant?.trim()
                val merchantsMatch =
                  !existingMerchant.isNullOrBlank() &&
                    merchantsEquivalent(safeMerchant, existingMerchant)

                existing.status != DetectionEventStatus.DISMISSED.name &&
                  existing.detectedType == parsed.type.name &&
                  abs(existing.receivedAt - parsed.timestamp) <= timeWindowMs &&
                  !distinctRefIds &&
                  sameOrCompatibleAccount &&
                  merchantsMatch
              }
            }

          val isAmbiguousRepeat = matchesRecentSavedTxn || matchesRecentPendingEvent

          // 5. Currency Check against Resolved Account
          val resolvedAccount = resolvedAccountId?.let { id -> accountsById[id] }
          val currencyMatchesAccount =
            resolvedAccount != null && resolvedAccount.currencyCode.equals(parsed.currencyCode, ignoreCase = true)

          // 6. Auto-Save vs Review Queue Decision
          val finalConfidenceReasons =
            if (isAmbiguousRepeat) {
              parsed.confidenceReasons + "Similar transaction detected within 10 minutes — routed to Review Queue"
            } else {
              parsed.confidenceReasons
            }

          val isSupportedAutomaticLedgerType =
            parsed.type == TransactionType.EXPENSE ||
              parsed.type == TransactionType.INCOME ||
              parsed.type == TransactionType.REFUND ||
              parsed.type == TransactionType.REVERSAL

          val shouldAutoSave =
            !isAmbiguousRepeat &&
              settings.isAutoSaveHighConfidenceEnabled &&
              parsed.confidenceLevel == DetectionConfidence.HIGH &&
              parsed.status == TransactionStatus.COMPLETED &&
              currencyMatchesAccount &&
              isSupportedAutomaticLedgerType

          val eventId = UUID.randomUUID().toString()
          var createdTxnId: String? = null
          val eventStatus: DetectionEventStatus
          val targetAutoSaveAccountId = if (shouldAutoSave) resolvedAccount?.id else null

          if (targetAutoSaveAccountId != null) {
            val defaultCategory =
              parsed.suggestedCategory
                ?: if (parsed.type == TransactionType.INCOME) "Other Income" else "Other Expenses"
            val newTxn =
              FinancialTransaction(
                id = UUID.randomUUID().toString(),
                userId = userId,
                accountId = targetAutoSaveAccountId,
                type = parsed.type,
                status = TransactionStatus.COMPLETED,
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                category = defaultCategory,
                merchantOrPayee = safeMerchant,
                notes = "Auto-detected via $safeTitle",
                transactionDate = parsed.timestamp,
                isAutomaticallyDetected = true,
                sourceApplication = rawPackageName,
                referenceId = parsed.referenceId,
                confidenceScore = parsed.confidenceScore,
              )

            val savedInTx = saveDetectedTransactionInCurrentTx(newTxn)
            if (savedInTx) {
              createdTxnId = newTxn.id
              eventStatus = DetectionEventStatus.AUTO_SAVED
              pendingNotice =
                AutoSavedTransactionNotice(
                  eventId = eventId,
                  transactionId = newTxn.id,
                  accountName = resolvedAccountName ?: "Account",
                  merchantOrPayee = safeMerchant ?: defaultCategory,
                  amountMinor = parsed.amountMinor,
                  currencyCode = parsed.currencyCode,
                  type = parsed.type,
                  sourceApplication = rawPackageName,
                  userId = userId,
                )
            } else {
              eventStatus = DetectionEventStatus.IN_REVIEW
            }
          } else {
            eventStatus = DetectionEventStatus.IN_REVIEW
          }

          val eventEntity =
            TransactionDetectionEventEntity(
              id = eventId,
              userId = userId,
              rawPackageName = rawPackageName,
              senderOrTitle = safeTitle,
              detectedAmountMinor = parsed.amountMinor,
              detectedCurrency = parsed.currencyCode,
              detectedType = parsed.type.name,
              detectedMerchant = safeMerchant,
              detectedAccountLast4 = parsed.accountLast4Hint,
              referenceId = parsed.referenceId,
              confidence = parsed.confidenceScore,
              confidenceLevel = parsed.confidenceLevel.name,
              confidenceReasonsJoined = finalConfidenceReasons.joinToString(";;;"),
              dedupFingerprint = fingerprint,
              status = eventStatus.name,
              mappedAccountId = resolvedAccountId,
              createdTransactionId = createdTxnId,
              receivedAt = parsed.timestamp,
            )

          eventDao.insertEvent(eventEntity)
          eventEntity.toDomain()
        }
        // Publish UI state only after Room commits; rolled-back transactions must not
        // produce a success notice.
        pendingNotice?.let { _latestAutoSavedNotice.value = it }
        processedEvent
      }
    }

  override suspend fun approveReviewEvent(
    userId: String,
    eventId: String,
    selectedAccountId: String,
    category: String,
    merchant: String?,
    correctedType: TransactionType?,
    correctedAmountMinor: Long?,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(eventId.isNotBlank()) { "Event ID is required" }
        require(selectedAccountId.isNotBlank()) { "Selected account ID is required" }
        database.withTransaction {
          val event =
            eventDao.getEventById(userId, eventId)
              ?: throw SecurityException("Detection event does not belong to authenticated user")
          require(event.userId == userId) { "Detection event ownership verification failed" }
          if (event.status != DetectionEventStatus.IN_REVIEW.name) {
            throw IllegalStateException("Event is not in review")
          }

          val parsedType =
            correctedType
              ?: runCatching { TransactionType.valueOf(event.detectedType) }
                .getOrDefault(TransactionType.EXPENSE)
          val finalType =
            if (parsedType == TransactionType.PENDING) TransactionType.EXPENSE else parsedType
          require(finalType != TransactionType.TRANSFER) {
            "Transfer detections require both source and destination accounts; create a paired transfer from Transactions."
          }
          val finalAmount = correctedAmountMinor ?: event.detectedAmountMinor
          val safeMerchant = sanitizeMerchant(merchant ?: event.detectedMerchant)

          val targetAccount =
            accountDao.getAccountById(userId, selectedAccountId)
              ?: throw SecurityException("Selected account does not belong to authenticated user")
          require(targetAccount.userId == userId) { "Selected account ownership verification failed" }
          require(targetAccount.currencyCode.equals(event.detectedCurrency, ignoreCase = true)) {
            "Currency mismatch: transaction currency ${event.detectedCurrency} does not match account currency ${targetAccount.currencyCode}"
          }

          val newTxn =
            FinancialTransaction(
              id = UUID.randomUUID().toString(),
              userId = userId,
              accountId = selectedAccountId,
              type = finalType,
              status =
                if (finalType == TransactionType.FAILED) TransactionStatus.FAILED
                else TransactionStatus.COMPLETED,
              amountMinor = finalAmount,
              currencyCode = event.detectedCurrency,
              category = category,
              merchantOrPayee = safeMerchant,
              notes = "Approved from Review Queue (Source: ${event.rawPackageName})",
              transactionDate = event.receivedAt,
              isAutomaticallyDetected = true,
              sourceApplication = event.rawPackageName,
              referenceId = event.referenceId,
              confidenceScore = event.confidence,
            )

          check(saveDetectedTransactionInCurrentTx(newTxn)) {
            "Failed to save approved transaction atomically"
          }

          val updatedRows =
            eventDao.updateEvent(
              event =
                event.copy(
                  userId = event.userId,
                  status = DetectionEventStatus.APPROVED.name,
                  detectedType = finalType.name,
                  detectedAmountMinor = finalAmount,
                  detectedMerchant = safeMerchant,
                  mappedAccountId = selectedAccountId,
                  createdTransactionId = newTxn.id,
                ),
              authenticatedUserId = userId,
            )
          check(updatedRows == 1) { "Detection event approval rejected for authenticated user" }

          if (!event.detectedAccountLast4.isNullOrBlank()) {
            val existingRule = mappingDao.findRuleForPattern(userId, event.detectedAccountLast4)
            if (existingRule == null) {
              mappingDao.insertRule(
                AccountMappingRuleEntity(
                  id = UUID.randomUUID().toString(),
                  userId = userId,
                  accountId = selectedAccountId,
                  matchPattern = event.detectedAccountLast4,
                  institutionHint = event.senderOrTitle,
                  isAutoMapped = true,
                  createdAt = System.currentTimeMillis(),
                )
              )
            }
          }
        }
      }
    }

  private suspend fun saveDetectedTransactionInCurrentTx(transaction: FinancialTransaction): Boolean {
    val account = accountDao.getAccountById(transaction.userId, transaction.accountId) ?: return false
    if (!account.currencyCode.equals(transaction.currencyCode, ignoreCase = true)) return false

    val effectiveStatus =
      when (transaction.type) {
        TransactionType.PENDING -> TransactionStatus.PENDING
        TransactionType.FAILED -> TransactionStatus.FAILED
        else -> transaction.status
      }
    val normalizedTxn = transaction.copy(status = effectiveStatus)
    transactionDao.insertTransaction(FinancialTransactionEntity.fromDomain(normalizedTxn))

    val delta =
      if (normalizedTxn.status != TransactionStatus.COMPLETED) 0L
      else when (normalizedTxn.type) {
        TransactionType.EXPENSE -> -normalizedTxn.amountMinor
        TransactionType.INCOME, TransactionType.REFUND, TransactionType.REVERSAL -> normalizedTxn.amountMinor
        TransactionType.TRANSFER, TransactionType.PENDING, TransactionType.FAILED -> 0L
      }
    if (delta != 0L) {
      accountDao.adjustBalance(normalizedTxn.userId, normalizedTxn.accountId, delta, normalizedTxn.updatedAt)
    }
    return true
  }

  override suspend fun dismissReviewEvent(userId: String, eventId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(eventId.isNotBlank()) { "Event ID is required" }
        database.withTransaction {
          val event =
            eventDao.getEventById(userId, eventId)
              ?: throw SecurityException("Detection event does not belong to authenticated user")
          require(event.userId == userId) { "Detection event ownership verification failed" }

          if (event.status == DetectionEventStatus.DISMISSED.name) {
            return@withTransaction
          }
          if (event.status != DetectionEventStatus.IN_REVIEW.name) {
            throw IllegalStateException("Event is not in review")
          }

          val updatedRows =
            eventDao.updateEvent(
              event = event.copy(status = DetectionEventStatus.DISMISSED.name),
              authenticatedUserId = userId,
            )
          check(updatedRows == 1) { "Detection event dismissal rejected for authenticated user" }
        }
      }
    }

  override suspend fun clearDetectionHistory(userId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        eventDao.clearAllEvents(userId)
      }
    }

  override fun getAccountMappingRules(userId: String): Flow<List<AccountMappingRule>> =
    mappingDao.getAllRules(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun addAccountMappingRule(
    authenticatedUserId: String,
    rule: AccountMappingRule,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(rule.userId == authenticatedUserId) {
          "Mapping rule ownership cannot be assigned across users"
        }
        database.withTransaction {
          val ownerAccount =
            accountDao.getAccountById(authenticatedUserId, rule.accountId)
              ?: throw SecurityException("Mapping rule target account does not belong to user")
          require(ownerAccount.userId == authenticatedUserId) { "Mapping rule ownership verification failed" }
          mappingDao.insertRule(AccountMappingRuleEntity.fromDomain(rule.copy(userId = authenticatedUserId)))
        }
      }
    }

  override suspend fun deleteAccountMappingRule(userId: String, ruleId: String): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(userId.isNotBlank()) { "Authenticated user ID is required" }
        require(ruleId.isNotBlank()) { "Rule ID is required" }
        mappingDao.deleteRule(userId, ruleId)
      }
    }

  override fun getDetectionSettings(userId: String): Flow<DetectionSettings> =
    context.detectionDataStore.data
      .map { prefs ->
        val notifEnabled = prefs[booleanPreferencesKey("notif_detection_$userId")] ?: true
        val autoSave = prefs[booleanPreferencesKey("autosave_high_conf_$userId")] ?: true
        val packages =
          prefs[stringSetPreferencesKey("permitted_pkgs_$userId")] ?: DetectionSettings.DEFAULT_PERMITTED_PACKAGES

        DetectionSettings(
          userId = userId,
          isNotificationDetectionEnabled = notifEnabled,
          isAutoSaveHighConfidenceEnabled = autoSave,
          isSmsDetectionEnabled = false, // Blocked per Google Play SMS & Call Log Policy
          isSmsPolicyRestricted = true,
          permittedPackages = packages,
        )
      }
      .flowOn(dispatchers.io)

  override suspend fun updateDetectionSettings(
    authenticatedUserId: String,
    settings: DetectionSettings,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(settings.userId == authenticatedUserId) {
          "Detection settings ownership cannot be modified across users"
        }
        context.detectionDataStore.edit { prefs ->
          prefs[booleanPreferencesKey("notif_detection_$authenticatedUserId")] =
            settings.isNotificationDetectionEnabled
          prefs[booleanPreferencesKey("autosave_high_conf_$authenticatedUserId")] =
            settings.isAutoSaveHighConfidenceEnabled
          prefs[stringSetPreferencesKey("permitted_pkgs_$authenticatedUserId")] =
            settings.permittedPackages
        }
        Unit
      }
    }

  private fun merchantsEquivalent(a: String, b: String): Boolean {
    val normA = a.trim().lowercase()
    val normB = b.trim().lowercase()
    if (normA == normB) return true
    if (normA.length >= 3 && normB.length >= 3) {
      return normA.contains(normB) || normB.contains(normA)
    }
    return false
  }

  private fun sanitizeMerchant(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val masked = trimmed.replace(Regex("[0-9]{6,}"), "••••").trim()
    return masked.take(36).trim().ifBlank { null }
  }

  private fun sanitizeSenderTitle(rawTitle: String): String =
    rawTitle.replace(Regex("[0-9]{6,}"), "••••").take(64).ifBlank { "Financial Alert" }
}
