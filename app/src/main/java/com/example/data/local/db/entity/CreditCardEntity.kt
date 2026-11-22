package com.example.data.local.db.entity

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
  val isActive: Boolean,
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
      isActive = isActive,
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
        isActive = domain.isActive,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
      )
  }
}
