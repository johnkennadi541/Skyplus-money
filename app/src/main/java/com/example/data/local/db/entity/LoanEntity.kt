package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Loan
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType

@Entity(
  tableName = "loans",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "status"]),
    Index(value = ["userId", "currencyCode"]),
    Index(value = ["userId", "nextDueDate"]),
  ],
)
data class LoanEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val lenderName: String,
  val loanType: String,
  val principalMinor: Long,
  val currencyCode: String,
  val annualInterestRatePercent: String,
  val tenureMonths: Int,
  val startDate: Long,
  val nextDueDate: Long,
  val monthlyEmiMinor: Long,
  val totalPayableMinor: Long,
  val totalInterestMinor: Long,
  val amountPaidMinor: Long,
  val outstandingPrincipalMinor: Long,
  val status: String,
  val notes: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): Loan =
    Loan(
      id = id,
      userId = userId,
      name = name,
      lenderName = lenderName,
      loanType = runCatching { LoanType.valueOf(loanType) }.getOrDefault(LoanType.OTHER),
      principalMinor = principalMinor,
      currencyCode = currencyCode,
      annualInterestRatePercent = annualInterestRatePercent,
      tenureMonths = tenureMonths,
      startDate = startDate,
      nextDueDate = nextDueDate,
      monthlyEmiMinor = monthlyEmiMinor,
      totalPayableMinor = totalPayableMinor,
      totalInterestMinor = totalInterestMinor,
      amountPaidMinor = amountPaidMinor,
      outstandingPrincipalMinor = outstandingPrincipalMinor,
      status = runCatching { LoanStatus.valueOf(status) }.getOrDefault(LoanStatus.ACTIVE),
      notes = notes,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(loan: Loan): LoanEntity =
      LoanEntity(
        id = loan.id,
        userId = loan.userId,
        name = loan.name,
        lenderName = loan.lenderName,
        loanType = loan.loanType.name,
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
        status = loan.status.name,
        notes = loan.notes,
        createdAt = loan.createdAt,
        updatedAt = loan.updatedAt,
      )
  }
}
