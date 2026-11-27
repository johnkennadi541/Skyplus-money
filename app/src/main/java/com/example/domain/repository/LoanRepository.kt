package com.example.domain.repository

import com.example.domain.model.Loan
import com.example.domain.model.LoanPayment
import kotlinx.coroutines.flow.Flow

interface LoanRepository {
  fun getLoans(userId: String): Flow<List<Loan>>

  fun getLoanByIdFlow(userId: String, loanId: String): Flow<Loan?>

  suspend fun getLoanById(userId: String, loanId: String): Loan?

  fun getPaymentsForLoan(userId: String, loanId: String): Flow<List<LoanPayment>>

  fun getAllPaymentsForUser(userId: String): Flow<List<LoanPayment>>

  suspend fun createLoan(
    authenticatedUserId: String,
    loan: Loan,
  ): Result<Loan>

  suspend fun updateLoan(
    authenticatedUserId: String,
    loan: Loan,
  ): Result<Unit>

  suspend fun markLoanCompleted(
    authenticatedUserId: String,
    loanId: String,
  ): Result<Unit>

  suspend fun recordPayment(
    authenticatedUserId: String,
    loanId: String,
    paymentDate: Long,
    amountMinor: Long,
    idempotencyKey: String? = null,
    notes: String? = null,
  ): Result<LoanPayment>

  fun getArchivedLoans(userId: String): Flow<List<Loan>>

  suspend fun archiveLoan(authenticatedUserId: String, loanId: String): Result<Unit>

  suspend fun restoreLoan(authenticatedUserId: String, loanId: String): Result<Unit>

  /** Legacy compatibility: archive instead of permanently deleting payment history. */
  suspend fun deleteLoan(
    authenticatedUserId: String,
    loanId: String,
  ): Result<Unit>
}
