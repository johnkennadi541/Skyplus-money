package com.example.domain.model

import java.math.BigDecimal
import java.util.UUID

enum class LoanType(val displayName: String) {
  PERSONAL("Personal Loan"),
  HOME("Home Loan"),
  VEHICLE("Vehicle Loan"),
  EDUCATION("Education Loan"),
  OTHER("Other Loan"),
}

enum class LoanStatus(val displayName: String) {
  ACTIVE("Active"),
  COMPLETED("Completed"),
  OVERDUE("Overdue"),
}

enum class LoanPaymentStatus(val displayName: String) {
  COMPLETED("Completed"),
  PENDING("Pending"),
  FAILED("Failed"),
}

enum class TenureUnit(val displayName: String) {
  MONTHS("Months"),
  YEARS("Years"),
}

data class Loan(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val lenderName: String,
  val loanType: LoanType,
  val principalMinor: Long,
  val currencyCode: String,
  val annualInterestRatePercent: String,
  val tenureMonths: Int,
  val startDate: Long,
  val nextDueDate: Long,
  val monthlyEmiMinor: Long,
  val totalPayableMinor: Long,
  val totalInterestMinor: Long,
  val amountPaidMinor: Long = 0L,
  val outstandingPrincipalMinor: Long = principalMinor,
  val status: LoanStatus = LoanStatus.ACTIVE,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

data class LoanPayment(
  val id: String = UUID.randomUUID().toString(),
  val loanId: String,
  val userId: String,
  val paymentDate: Long,
  val amountMinor: Long,
  val principalComponentMinor: Long,
  val interestComponentMinor: Long,
  val remainingBalanceMinor: Long,
  val currencyCode: String,
  val status: LoanPaymentStatus = LoanPaymentStatus.COMPLETED,
  val idempotencyKey: String,
  val notes: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

data class EmiCalculationResult(
  val principalMoney: Money,
  val annualInterestRatePercent: BigDecimal,
  val tenureMonths: Int,
  val monthlyEmiMoney: Money,
  val totalRepaymentMoney: Money,
  val totalInterestMoney: Money,
  val principalSharePercent: BigDecimal,
  val interestSharePercent: BigDecimal,
)

data class PaymentBreakdown(
  val paymentMoney: Money,
  val principalComponentMoney: Money,
  val interestComponentMoney: Money,
  val remainingBalanceMoney: Money,
)

data class LoanCurrencySummary(
  val currencyCode: String,
  val totalPrincipalMinor: Long,
  val totalOutstandingMinor: Long,
  val totalAmountPaidMinor: Long,
  val totalMonthlyEmiMinor: Long,
  val activeLoanCount: Int,
  val overdueLoanCount: Int,
  val completedLoanCount: Int,
)

data class EmiPrefillDraft(
  val principalDecimal: String,
  val annualRatePercent: String,
  val tenureMonths: Int,
  val currencyCode: String,
)
