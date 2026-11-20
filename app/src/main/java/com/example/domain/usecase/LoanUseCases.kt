package com.example.domain.usecase

import com.example.domain.model.Loan
import com.example.domain.model.LoanCurrencySummary
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.Money
import com.example.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveLoansWithSummariesUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
  private val emiCalculationEngine: EmiCalculationEngine,
) {
  operator fun invoke(
    userId: String,
    nowProvider: () -> Long = { System.currentTimeMillis() },
  ): Flow<Pair<List<Loan>, Map<String, LoanCurrencySummary>>> {
    return loanRepository.getLoans(userId).map { loans ->
      val now = nowProvider()
      val effectiveLoans =
        loans.map { loan ->
          loan.copy(status = emiCalculationEngine.evaluateEffectiveStatus(loan, now))
        }
      val summaries = emiCalculationEngine.calculateCurrencySummaries(effectiveLoans, now)
      effectiveLoans to summaries
    }
  }
}

@Singleton
class ObserveLoanDetailUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
  private val emiCalculationEngine: EmiCalculationEngine,
) {
  operator fun invoke(
    userId: String,
    loanId: String,
    nowProvider: () -> Long = { System.currentTimeMillis() },
  ): Flow<Pair<Loan?, List<LoanPayment>>> {
    return combine(
      loanRepository.getLoanByIdFlow(userId, loanId),
      loanRepository.getPaymentsForLoan(userId, loanId),
    ) { loan, payments ->
      val now = nowProvider()
      val effectiveLoan =
        loan?.copy(status = emiCalculationEngine.evaluateEffectiveStatus(loan, now))
      effectiveLoan to payments
    }
  }
}

@Singleton
class CreateLoanUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
  private val emiCalculationEngine: EmiCalculationEngine,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    name: String,
    lenderName: String,
    loanType: LoanType,
    principalDecimal: String,
    currencyCode: String,
    annualInterestRateText: String,
    tenureMonths: Int,
    startDate: Long,
    alreadyPaidDecimal: String = "0",
    nextDueDate: Long? = null,
    notes: String? = null,
  ): Result<Loan> {
    val normalizedCurrency = currencyCode.trim().uppercase()
    val principalMoney =
      runCatching {
        Money.fromBigDecimal(BigDecimal(principalDecimal.trim()), normalizedCurrency, RoundingMode.HALF_UP)
      }.getOrElse {
        return Result.failure(IllegalArgumentException("Enter a valid numeric principal amount"))
      }

    if (principalMoney.minorUnits <= 0L) {
      return Result.failure(IllegalArgumentException("Loan principal must be greater than zero"))
    }

    val rate =
      runCatching { BigDecimal(annualInterestRateText.trim().ifEmpty { "0" }) }.getOrElse {
        return Result.failure(IllegalArgumentException("Enter a valid numeric interest rate"))
      }

    val emiResult =
      runCatching {
        emiCalculationEngine.calculateEmi(
          principalMinor = principalMoney.minorUnits,
          annualInterestRatePercent = rate,
          tenureMonths = tenureMonths,
          currencyCode = normalizedCurrency,
        )
      }.getOrElse {
        return Result.failure(it)
      }

    val paidText = alreadyPaidDecimal.trim().ifEmpty { "0" }
    val alreadyPaidMoney =
      runCatching {
        Money.fromBigDecimal(BigDecimal(paidText), normalizedCurrency, RoundingMode.HALF_UP)
      }.getOrElse {
        return Result.failure(IllegalArgumentException("Enter a valid numeric amount already paid"))
      }

    if (alreadyPaidMoney.minorUnits < 0L) {
      return Result.failure(IllegalArgumentException("Amount already paid cannot be negative"))
    }
    if (alreadyPaidMoney.minorUnits > principalMoney.minorUnits) {
      return Result.failure(
        IllegalArgumentException("Amount already paid toward principal cannot exceed original principal")
      )
    }

    val outstandingPrincipal = principalMoney.minorUnits - alreadyPaidMoney.minorUnits
    val resolvedNextDueDate = nextDueDate ?: emiCalculationEngine.addMonths(startDate, 1)
    val initialStatus =
      if (outstandingPrincipal == 0L) LoanStatus.COMPLETED else LoanStatus.ACTIVE

    val loan =
      Loan(
        userId = authenticatedUserId,
        name = name.trim(),
        lenderName = lenderName.trim(),
        loanType = loanType,
        principalMinor = principalMoney.minorUnits,
        currencyCode = normalizedCurrency,
        annualInterestRatePercent = rate.stripTrailingZeros().toPlainString(),
        tenureMonths = tenureMonths,
        startDate = startDate,
        nextDueDate = resolvedNextDueDate,
        monthlyEmiMinor = emiResult.monthlyEmiMoney.minorUnits,
        totalPayableMinor = emiResult.totalRepaymentMoney.minorUnits,
        totalInterestMinor = emiResult.totalInterestMoney.minorUnits,
        amountPaidMinor = alreadyPaidMoney.minorUnits,
        outstandingPrincipalMinor = outstandingPrincipal,
        status = initialStatus,
        notes = notes?.trim()?.ifBlank { null },
      )

    return loanRepository.createLoan(authenticatedUserId, loan)
  }
}

@Singleton
class UpdateLoanUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    loan: Loan,
  ): Result<Unit> = loanRepository.updateLoan(authenticatedUserId, loan)
}

@Singleton
class RecordLoanPaymentUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    loanId: String,
    paymentDate: Long,
    amountMinor: Long,
    idempotencyKey: String? = null,
    notes: String? = null,
  ): Result<LoanPayment> =
    loanRepository.recordPayment(
      authenticatedUserId = authenticatedUserId,
      loanId = loanId,
      paymentDate = paymentDate,
      amountMinor = amountMinor,
      idempotencyKey = idempotencyKey,
      notes = notes,
    )
}

@Singleton
class MarkLoanCompletedUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    loanId: String,
  ): Result<Unit> = loanRepository.markLoanCompleted(authenticatedUserId, loanId)
}

@Singleton
class DeleteLoanUseCase @Inject constructor(
  private val loanRepository: LoanRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    loanId: String,
  ): Result<Unit> = loanRepository.deleteLoan(authenticatedUserId, loanId)
}
