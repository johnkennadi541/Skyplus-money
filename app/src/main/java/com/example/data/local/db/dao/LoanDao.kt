package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.LoanEntity
import com.example.data.local.db.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

  @Query("SELECT * FROM loans WHERE userId = :userId AND isArchived = 0 ORDER BY createdAt DESC")
  fun getAllLoans(userId: String): Flow<List<LoanEntity>>

  @Query("SELECT * FROM loans WHERE userId = :userId AND isArchived = 1 ORDER BY createdAt DESC")
  fun getArchivedLoans(userId: String): Flow<List<LoanEntity>>

  @Query("UPDATE loans SET isArchived = :archived, updatedAt = :updatedAt WHERE userId = :userId AND id = :loanId")
  suspend fun setLoanArchived(userId: String, loanId: String, archived: Boolean, updatedAt: Long): Int

  @Query("SELECT * FROM loans WHERE userId = :userId AND id = :loanId LIMIT 1")
  suspend fun getLoanById(userId: String, loanId: String): LoanEntity?

  @Query("SELECT * FROM loans WHERE userId = :userId AND id = :loanId LIMIT 1")
  fun getLoanByIdFlow(userId: String, loanId: String): Flow<LoanEntity?>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertLoan(loan: LoanEntity)

  @Query(
    """
    UPDATE loans
    SET name = :name,
        lenderName = :lenderName,
        loanType = :loanType,
        principalMinor = :principalMinor,
        currencyCode = :currencyCode,
        annualInterestRatePercent = :annualInterestRatePercent,
        tenureMonths = :tenureMonths,
        startDate = :startDate,
        nextDueDate = :nextDueDate,
        monthlyEmiMinor = :monthlyEmiMinor,
        totalPayableMinor = :totalPayableMinor,
        totalInterestMinor = :totalInterestMinor,
        amountPaidMinor = :amountPaidMinor,
        outstandingPrincipalMinor = :outstandingPrincipalMinor,
        status = :status,
        notes = :notes,
        updatedAt = :updatedAt
    WHERE id = :id AND userId = :userId
    """
  )
  suspend fun updateLoanScoped(
    id: String,
    userId: String,
    name: String,
    lenderName: String,
    loanType: String,
    principalMinor: Long,
    currencyCode: String,
    annualInterestRatePercent: String,
    tenureMonths: Int,
    startDate: Long,
    nextDueDate: Long,
    monthlyEmiMinor: Long,
    totalPayableMinor: Long,
    totalInterestMinor: Long,
    amountPaidMinor: Long,
    outstandingPrincipalMinor: Long,
    status: String,
    notes: String?,
    updatedAt: Long,
  ): Int

  suspend fun updateLoan(loan: LoanEntity, authenticatedUserId: String): Int {
    if (authenticatedUserId.isBlank() || loan.userId != authenticatedUserId) return 0
    return updateLoanScoped(
      id = loan.id,
      userId = authenticatedUserId,
      name = loan.name,
      lenderName = loan.lenderName,
      loanType = loan.loanType,
      principalMinor = loan.principalMinor,
      currencyCode = loan.currencyCode,
      annualInterestRatePercent = loan.annualInterestRatePercent,
      tenureMonths = loan.tenureMonths,
      startDate = loan.startDate,
      nextDueDate = loan.nextDueDate,
      monthlyEmiMinor = loan.monthlyEmiMinor,
      totalPayableMinor = loan.totalPayableMinor,
      totalInterestMinor = loan.totalInterestMinor,
      amountPaidMinor = loan.amountPaidMinor,
      outstandingPrincipalMinor = loan.outstandingPrincipalMinor,
      status = loan.status,
      notes = loan.notes,
      updatedAt = loan.updatedAt,
    )
  }

  @Query(
    """
    SELECT * FROM loan_payments 
    WHERE userId = :userId AND loanId = :loanId 
    ORDER BY paymentDate DESC, createdAt DESC
    """
  )
  fun getPaymentsForLoan(userId: String, loanId: String): Flow<List<LoanPaymentEntity>>

  @Query(
    """
    SELECT * FROM loan_payments 
    WHERE userId = :userId 
    ORDER BY paymentDate DESC, createdAt DESC
    """
  )
  fun getAllPaymentsForUser(userId: String): Flow<List<LoanPaymentEntity>>

  @Query(
    """
    SELECT * FROM loan_payments 
    WHERE userId = :userId AND loanId = :loanId 
    ORDER BY paymentDate DESC, createdAt DESC
    """
  )
  suspend fun getPaymentsForLoanSync(userId: String, loanId: String): List<LoanPaymentEntity>

  @Query(
    """
    SELECT * FROM loan_payments 
    WHERE userId = :userId AND loanId = :loanId AND idempotencyKey = :idempotencyKey 
    LIMIT 1
    """
  )
  suspend fun findDuplicateByIdempotencyKey(
    userId: String,
    loanId: String,
    idempotencyKey: String,
  ): LoanPaymentEntity?

  @Query(
    """
    SELECT * FROM loan_payments
    WHERE userId = :userId
      AND loanId = :loanId
      AND amountMinor = :amountMinor
      AND paymentDate BETWEEN :dayStartMillis AND :dayEndMillis
    LIMIT 1
    """
  )
  suspend fun findDuplicateByDayAndAmount(
    userId: String,
    loanId: String,
    amountMinor: Long,
    dayStartMillis: Long,
    dayEndMillis: Long,
  ): LoanPaymentEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPayment(payment: LoanPaymentEntity)

}
