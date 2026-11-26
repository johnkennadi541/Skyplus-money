package com.example.domain.model

import java.util.UUID

enum class AccountType {
  BANK,
  CASH,
  SAVINGS,
  WALLET,
  CREDIT_CARD,
}

data class FinancialAccount(
  val id: String = UUID.randomUUID().toString(),
  val userId: String,
  val name: String,
  val type: AccountType,
  val currencyCode: String,
  val initialBalanceMinor: Long,
  val currentBalanceMinor: Long,
  val institutionName: String? = null,
  val accountNumberLast4: String? = null,
  val colorHex: String = "#0A2540",
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val initialBalance: Money
    get() = Money.fromMinorUnits(initialBalanceMinor, currencyCode)

  val currentBalance: Money
    get() = Money.fromMinorUnits(currentBalanceMinor, currencyCode)
}
