package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.LoanPayment
import com.example.domain.model.LoanPaymentStatus

@Entity(
  tableName = "loan_payments",
  foreignKeys = [
    ForeignKey(
      entity = LoanEntity::class,
      parentColumns = ["id"],
      childColumns = ["loanId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["loanId"]),
    Index(value = ["userId", "loanId", "paymentDate"]),
    Index(value = ["userId", "loanId", "idempotencyKey"], unique = true),
  ],
)
data class LoanPaymentEntity(
  @PrimaryKey val id: String,
  val loanId: String,
  val userId: String,
  val paymentDate: Long,
  val amountMinor: Long,
  val principalComponentMinor: Long,
  val interestComponentMinor: Long,
  val remainingBalanceMinor: Long,
  val currencyCode: String,
  val status: String,
  val idempotencyKey: String,
  val notes: String? = null,
  val createdAt: Long,
) {
  fun toDomain(): LoanPayment =
    LoanPayment(
      id = id,
      loanId = loanId,
      userId = userId,
      paymentDate = paymentDate,
      amountMinor = amountMinor,
      principalComponentMinor = principalComponentMinor,
      interestComponentMinor = interestComponentMinor,
      remainingBalanceMinor = remainingBalanceMinor,
      currencyCode = currencyCode,
      status = runCatching { LoanPaymentStatus.valueOf(status) }.getOrDefault(LoanPaymentStatus.COMPLETED),
      idempotencyKey = idempotencyKey,
      notes = notes,
      createdAt = createdAt,
    )

  companion object {
    fun fromDomain(payment: LoanPayment): LoanPaymentEntity =
      LoanPaymentEntity(
        id = payment.id,
        loanId = payment.loanId,
        userId = payment.userId,
        paymentDate = payment.paymentDate,
        amountMinor = payment.amountMinor,
        principalComponentMinor = payment.principalComponentMinor,
        interestComponentMinor = payment.interestComponentMinor,
        remainingBalanceMinor = payment.remainingBalanceMinor,
        currencyCode = payment.currencyCode,
        status = payment.status.name,
        idempotencyKey = payment.idempotencyKey,
        notes = payment.notes,
        createdAt = payment.createdAt,
      )
  }
}
