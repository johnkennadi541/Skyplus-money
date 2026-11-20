package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountMappingRule

@Entity(
  tableName = "account_mapping_rules",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "accountId"]),
    Index(value = ["userId", "matchPattern"]),
  ],
)
data class AccountMappingRuleEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val accountId: String,
  val matchPattern: String,
  val institutionHint: String?,
  val isAutoMapped: Boolean,
  val createdAt: Long,
) {
  fun toDomain(): AccountMappingRule =
    AccountMappingRule(
      id = id,
      userId = userId,
      accountId = accountId,
      matchPattern = matchPattern,
      institutionHint = institutionHint,
      isAutoMapped = isAutoMapped,
      createdAt = createdAt,
    )

  companion object {
    fun fromDomain(rule: AccountMappingRule): AccountMappingRuleEntity =
      AccountMappingRuleEntity(
        id = rule.id,
        userId = rule.userId,
        accountId = rule.accountId,
        matchPattern = rule.matchPattern,
        institutionHint = rule.institutionHint,
        isAutoMapped = rule.isAutoMapped,
        createdAt = rule.createdAt,
      )
  }
}
