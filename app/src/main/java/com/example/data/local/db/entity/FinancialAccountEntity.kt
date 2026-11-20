package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountType
import com.example.domain.model.FinancialAccount

@Entity(
  tableName = "financial_accounts",
  indices = [Index(value = ["userId"]), Index(value = ["userId", "name"])],
)
data class FinancialAccountEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val accountType: String,
  val currencyCode: String,
  val initialBalanceMinor: Long,
  val currentBalanceMinor: Long,
  val institutionName: String?,
  val accountNumberLast4: String?,
  val colorHex: String,
  val isActive: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): FinancialAccount =
    FinancialAccount(
      id = id,
      userId = userId,
      name = name,
      type = runCatching { AccountType.valueOf(accountType) }.getOrDefault(AccountType.BANK),
      currencyCode = currencyCode,
      initialBalanceMinor = initialBalanceMinor,
      currentBalanceMinor = currentBalanceMinor,
      institutionName = institutionName,
      accountNumberLast4 = accountNumberLast4,
      colorHex = colorHex,
      isActive = isActive,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(account: FinancialAccount): FinancialAccountEntity =
      FinancialAccountEntity(
        id = account.id,
        userId = account.userId,
        name = account.name,
        accountType = account.type.name,
        currencyCode = account.currencyCode,
        initialBalanceMinor = account.initialBalanceMinor,
        currentBalanceMinor = account.currentBalanceMinor,
        institutionName = account.institutionName,
        accountNumberLast4 = account.accountNumberLast4,
        colorHex = account.colorHex,
        isActive = account.isActive,
        createdAt = account.createdAt,
        updatedAt = account.updatedAt,
      )
  }
}
