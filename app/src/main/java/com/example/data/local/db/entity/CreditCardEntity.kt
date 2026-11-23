package com.example.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork

@Entity(
  tableName = "credit_cards",
  indices = [
    Index(value = ["accountId"], unique = true),
    Index(value = ["userId"]),
  ],
)
data class CreditCardEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val accountId: String,
  val issuerName: String,
  val network: String,
  val last4: String,
  val creditLimitMinor: Long,
  val currencyCode: String,
  val billingCycleDay: Int,
  val paymentDueDayOffset: Int,
  val aprPercent: String,
  @ColumnInfo(defaultValue = "0") val joiningFeeMinor: Long = 0L,
  @ColumnInfo(defaultValue = "0") val annualFeeMinor: Long = 0L,
  @ColumnInfo(defaultValue = "1") val annualFeeMonth: Int = 1,
  val rewardProgramName: String?,
  val isActive: Boolean,
  @ColumnInfo(defaultValue = "0") val isFrozen: Boolean = false,
  @ColumnInfo(defaultValue = "1") val reminderEnabled: Boolean = true,
  @ColumnInfo(defaultValue = "3") val reminderDaysBefore: Int = 3,
  val notes: String?,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): CreditCard =
    CreditCard(
      id = id,
      userId = userId,
      accountId = accountId,
      issuerName = issuerName,
      network = runCatching { CreditCardNetwork.valueOf(network) }.getOrDefault(CreditCardNetwork.OTHER),
      last4 = last4,
      creditLimitMinor = creditLimitMinor,
      currencyCode = currencyCode,
      billingCycleDay = billingCycleDay,
      paymentDueDayOffset = paymentDueDayOffset,
      aprPercent = aprPercent,
      joiningFeeMinor = joiningFeeMinor,
      annualFeeMinor = annualFeeMinor,
      annualFeeMonth = annualFeeMonth,
      rewardProgramName = rewardProgramName,
      isActive = isActive,
      isFrozen = isFrozen,
      reminderEnabled = reminderEnabled,
      reminderDaysBefore = reminderDaysBefore,
      notes = notes,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(domain: CreditCard): CreditCardEntity =
      CreditCardEntity(
        id = domain.id,
        userId = domain.userId,
        accountId = domain.accountId,
        issuerName = domain.issuerName,
        network = domain.network.name,
        last4 = domain.last4,
        creditLimitMinor = domain.creditLimitMinor,
        currencyCode = domain.currencyCode,
        billingCycleDay = domain.billingCycleDay,
        paymentDueDayOffset = domain.paymentDueDayOffset,
        aprPercent = domain.aprPercent,
        joiningFeeMinor = domain.joiningFeeMinor,
        annualFeeMinor = domain.annualFeeMinor,
        annualFeeMonth = domain.annualFeeMonth,
        rewardProgramName = domain.rewardProgramName,
        isActive = domain.isActive,
        isFrozen = domain.isFrozen,
        reminderEnabled = domain.reminderEnabled,
        reminderDaysBefore = domain.reminderDaysBefore,
        notes = domain.notes,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
      )
  }
}
