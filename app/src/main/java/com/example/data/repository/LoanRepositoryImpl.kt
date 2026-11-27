package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.common.DispatcherProvider
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.dao.LoanDao
import com.example.data.local.db.entity.LoanEntity
import com.example.data.local.db.entity.LoanPaymentEntity
import com.example.domain.model.Loan
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanPaymentStatus
import com.example.domain.model.LoanStatus
import com.example.domain.model.Money
import com.example.domain.repository.LoanRepository
import com.example.domain.usecase.EmiCalculationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepositoryImpl @Inject constructor(
  private val database: AppDatabase,
  private val loanDao: LoanDao,
  private val emiCalculationEngine: EmiCalculationEngine,
  private val dispatchers: DispatcherProvider,
) : LoanRepository {

  override fun getArchivedLoans(userId: String): Flow<List<Loan>> =
    loanDao.getArchivedLoans(userId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

  override fun getLoans(userId: String): Flow<List<Loan>> =
    loanDao.getAllLoans(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getLoanByIdFlow(userId: String, loanId: String): Flow<Loan?> =
    loanDao.getLoanByIdFlow(userId, loanId)
      .map { it?.toDomain() }
      .flowOn(dispatchers.io)

  override suspend fun getLoanById(userId: String, loanId: String): Loan? =
    withContext(dispatchers.io) {
      loanDao.getLoanById(userId, loanId)?.toDomain()
    }

  override fun getPaymentsForLoan(userId: String, loanId: String): Flow<List<LoanPayment>> =
    loanDao.getPaymentsForLoan(userId, loanId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override fun getAllPaymentsForUser(userId: String): Flow<List<LoanPayment>> =
    loanDao.getAllPaymentsForUser(userId)
      .map { list -> list.map { it.toDomain() } }
      .flowOn(dispatchers.io)

  override suspend fun createLoan(
    authenticatedUserId: String,
    loan: Loan,
  ): Result<Loan> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(loan.userId == authenticatedUserId) {
          "Loan ownership cannot be assigned across users"
        }
        require(loan.name.isNotBlank()) { "Loan name cannot be blank" }
        require(loan.lenderName.isNotBlank()) { "Lender name cannot be blank" }
        require(loan.principalMinor > 0L) { "Loan principal must be strictly positive" }
        require(loan.amountPaidMinor >= 0L) { "Amount paid cannot be negative" }
        require(loan.outstandingPrincipalMinor in 0L..loan.principalMinor) {
          "Outstanding principal must be between 0 and original principal"
        }
        Money.resolveFractionDigits(loan.currencyCode)
        val rate = BigDecimal(loan.annualInterestRatePercent.trim().ifEmpty { "0" })
        val emiResult =
          emiCalculationEngine.calculateEmi(
            principalMinor = loan.principalMinor,
            annualInterestRatePercent = rate,
            tenureMonths = loan.tenureMonths,
            currencyCode = loan.currencyCode,
          )
        if (loan.status == LoanStatus.COMPLETED && loan.outstandingPrincipalMinor > 0L) {
          throw IllegalStateException("Cannot mark loan completed while outstanding principal remains")
        }

        val now = System.currentTimeMillis()
        val finalStatus =
          when {
            loan.outstandingPrincipalMinor == 0L -> LoanStatus.COMPLETED
            else -> loan.status
          }

        val normalizedLoan =
          loan.copy(
            userId = authenticatedUserId,
            name = loan.name.trim(),
            lenderName = loan.lenderName.trim(),
            currencyCode = emiResult.principalMoney.currencyCode,
            annualInterestRatePercent = rate.stripTrailingZeros().toPlainString(),
            monthlyEmiMinor = emiResult.monthlyEmiMoney.minorUnits,
            totalPayableMinor = emiResult.totalRepaymentMoney.minorUnits,
            totalInterestMinor = emiResult.totalInterestMoney.minorUnits,
            status = finalStatus,
            notes = loan.notes?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
          )

        database.withTransaction {
          loanDao.insertLoan(LoanEntity.fromDomain(normalizedLoan))
        }
        normalizedLoan
      }
    }

  override suspend fun updateLoan(
    authenticatedUserId: String,
    loan: Loan,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(loan.userId == authenticatedUserId) {
          "Loan ownership cannot be modified or transferred across users"
        }
        require(loan.name.isNotBlank()) { "Loan name cannot be blank" }
        require(loan.lenderName.isNotBlank()) { "Lender name cannot be blank" }
        require(loan.principalMinor > 0L) { "Loan principal must be strictly positive" }
        require(loan.amountPaidMinor >= 0L) { "Amount paid cannot be negative" }
        require(loan.outstandingPrincipalMinor in 0L..loan.principalMinor) {
          "Outstanding principal must be between 0 and original principal"
        }
        Money.resolveFractionDigits(loan.currencyCode)
        val rate = BigDecimal(loan.annualInterestRatePercent.trim().ifEmpty { "0" })
        val emiResult =
          emiCalculationEngine.calculateEmi(
            principalMinor = loan.principalMinor,
            annualInterestRatePercent = rate,
            tenureMonths = loan.tenureMonths,
            currencyCode = loan.currencyCode,
          )
        if (loan.status == LoanStatus.COMPLETED && loan.outstandingPrincipalMinor > 0L) {
          throw IllegalStateException("Cannot mark loan completed while outstanding principal remains")
        }

        database.withTransaction {
          val existing =
            loanDao.getLoanById(authenticatedUserId, loan.id)
              ?: throw SecurityException("Loan does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Loan ownership verification failed" }

          val now = System.currentTimeMillis()
          val finalStatus =
            when {
              loan.outstandingPrincipalMinor == 0L -> LoanStatus.COMPLETED
              loan.status == LoanStatus.COMPLETED -> LoanStatus.ACTIVE
              else -> loan.status
            }

          val updated =
            loan.copy(
              userId = existing.userId,
              name = loan.name.trim(),
              lenderName = loan.lenderName.trim(),
              currencyCode = emiResult.principalMoney.currencyCode,
              annualInterestRatePercent = rate.stripTrailingZeros().toPlainString(),
              monthlyEmiMinor = emiResult.monthlyEmiMoney.minorUnits,
              totalPayableMinor = emiResult.totalRepaymentMoney.minorUnits,
              totalInterestMinor = emiResult.totalInterestMoney.minorUnits,
              status = finalStatus,
              notes = loan.notes?.trim()?.ifBlank { null },
              createdAt = existing.createdAt,
              updatedAt = now,
            )

          val rowsUpdated = loanDao.updateLoan(LoanEntity.fromDomain(updated), authenticatedUserId)
          check(rowsUpdated == 1) { "Loan update rejected for authenticated user" }
        }
      }
    }

  override suspend fun markLoanCompleted(
    authenticatedUserId: String,
    loanId: String,
  ): Result<Unit> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(loanId.isNotBlank()) { "Loan ID is required" }

        database.withTransaction {
          val existing =
            loanDao.getLoanById(authenticatedUserId, loanId)
              ?: throw SecurityException("Loan does not belong to authenticated user")
          require(existing.userId == authenticatedUserId) { "Loan ownership verification failed" }
          if (existing.outstandingPrincipalMinor > 0L) {
            throw IllegalStateException(
              "Cannot mark loan as completed while outstanding principal remains"
            )
          }

          val updated =
            existing.copy(
              status = LoanStatus.COMPLETED.name,
              updatedAt = System.currentTimeMillis(),
            )
          val rowsUpdated = loanDao.updateLoan(updated, authenticatedUserId)
          check(rowsUpdated == 1) { "Loan completion rejected for authenticated user" }
        }
      }
    }

  override suspend fun recordPayment(
    authenticatedUserId: String,
    loanId: String,
    paymentDate: Long,
    amountMinor: Long,
    idempotencyKey: String?,
    notes: String?,
  ): Result<LoanPayment> =
    withContext(dispatchers.io) {
      runCatching {
        require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
        require(loanId.isNotBlank()) { "Loan ID is required" }
        require(paymentDate > 0L) { "Payment date must be valid" }
        require(amountMinor > 0L) { "Payment amount must be strictly positive" }

        val (dayStart, dayEnd) = dayBoundsMillis(paymentDate)
        val effectiveKey =
          idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${loanId}_${dayStart}_${amountMinor}"

        database.withTransaction {
          val existingLoan =
            loanDao.getLoanById(authenticatedUserId, loanId)
              ?: throw SecurityException("Loan does not belong to authenticated user")
          require(existingLoan.userId == authenticatedUserId) { "Loan ownership verification failed" }

          if (existingLoan.status == LoanStatus.COMPLETED.name || existingLoan.outstandingPrincipalMinor <= 0L) {
            throw IllegalStateException("This loan is already fully paid and completed")
          }

          val dupByKey =
            loanDao.findDuplicateByIdempotencyKey(authenticatedUserId, loanId, effectiveKey)
          if (dupByKey != null) {
            throw IllegalStateException("Duplicate payment prevented: this payment was already recorded")
          }

          val dupByDayAndAmount =
            loanDao.findDuplicateByDayAndAmount(
              userId = authenticatedUserId,
              loanId = loanId,
              amountMinor = amountMinor,
              dayStartMillis = dayStart,
              dayEndMillis = dayEnd,
            )
          if (dupByDayAndAmount != null) {
            throw IllegalStateException(
              "Duplicate payment prevented: an identical payment on this date already exists"
            )
          }

          val breakdown =
            emiCalculationEngine.calculatePaymentBreakdown(
              outstandingPrincipalMinor = existingLoan.outstandingPrincipalMinor,
              annualInterestRatePercent = existingLoan.annualInterestRatePercent,
              paymentAmountMinor = amountMinor,
              currencyCode = existingLoan.currencyCode,
            )

          val now = System.currentTimeMillis()
          val payment =
            LoanPayment(
              id = UUID.randomUUID().toString(),
              loanId = loanId,
              userId = authenticatedUserId,
              paymentDate = paymentDate,
              amountMinor = amountMinor,
              principalComponentMinor = breakdown.principalComponentMoney.minorUnits,
              interestComponentMinor = breakdown.interestComponentMoney.minorUnits,
              remainingBalanceMinor = breakdown.remainingBalanceMoney.minorUnits,
              currencyCode = existingLoan.currencyCode,
              status = LoanPaymentStatus.COMPLETED,
              idempotencyKey = effectiveKey,
              notes = notes?.trim()?.ifBlank { null },
              createdAt = now,
            )

          loanDao.insertPayment(LoanPaymentEntity.fromDomain(payment))

          val newAmountPaid = Math.addExact(existingLoan.amountPaidMinor, amountMinor)
          val newOutstanding = breakdown.remainingBalanceMoney.minorUnits
          val newStatus =
            if (newOutstanding == 0L) LoanStatus.COMPLETED.name else LoanStatus.ACTIVE.name
          val newNextDueDate =
            if (newOutstanding == 0L) {
              existingLoan.nextDueDate
            } else {
              emiCalculationEngine.addMonths(existingLoan.nextDueDate, 1)
            }

          val updatedLoan =
            existingLoan.copy(
              amountPaidMinor = newAmountPaid,
              outstandingPrincipalMinor = newOutstanding,
              status = newStatus,
              nextDueDate = newNextDueDate,
              updatedAt = now,
            )
          val rowsUpdated = loanDao.updateLoan(updatedLoan, authenticatedUserId)
          check(rowsUpdated == 1) { "Failed to update loan balance after payment" }

          payment
        }
      }
    }

  override suspend fun archiveLoan(authenticatedUserId: String, loanId: String): Result<Unit> =
    setLoanArchived(authenticatedUserId, loanId, archived = true)

  override suspend fun restoreLoan(authenticatedUserId: String, loanId: String): Result<Unit> =
    setLoanArchived(authenticatedUserId, loanId, archived = false)

  override suspend fun deleteLoan(authenticatedUserId: String, loanId: String): Result<Unit> =
    archiveLoan(authenticatedUserId, loanId)

  private suspend fun setLoanArchived(
    authenticatedUserId: String,
    loanId: String,
    archived: Boolean,
  ): Result<Unit> = withContext(dispatchers.io) {
    runCatching {
      require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
      require(loanId.isNotBlank()) { "Loan ID is required" }
      database.withTransaction {
        val existing = loanDao.getLoanById(authenticatedUserId, loanId)
          ?: throw SecurityException("Loan does not belong to authenticated user")
        require(existing.userId == authenticatedUserId) { "Loan ownership verification failed" }
        check(existing.isArchived != archived) {
          if (archived) "Loan is already archived" else "Loan is not archived"
        }
        val rows = loanDao.setLoanArchived(authenticatedUserId, loanId, archived, System.currentTimeMillis())
        check(rows == 1) { "Loan archive state update rejected for authenticated user" }
      }
    }
  }

  private fun dayBoundsMillis(timestampMillis: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestampMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis

    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    val end = cal.timeInMillis
    return start to end
  }
}
