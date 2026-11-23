package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CreditCardStatement

@Entity(
  tableName = "credit_card_statements",
  foreignKeys = [
    ForeignKey(
      entity = CreditCardEntity::class,
      parentColumns = ["id"],
      childColumns = ["cardId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["userId", "cardId", "statementDate"], unique = true),
    Index(value = ["userId", "dueDate"]),
  ],
)
data class CreditCardStatementEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val cardId: String,
  val currencyCode: String,
  val statementDate: Long,
  val dueDate: Long,
  val statementBalanceMinor: Long,
  val minimumDueMinor: Long,
  val feesMinor: Long,
  val interestMinor: Long,
  val notes: String?,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain() = CreditCardStatement(
    id = id,
    userId = userId,
    cardId = cardId,
    currencyCode = currencyCode,
    statementDate = statementDate,
    dueDate = dueDate,
    statementBalanceMinor = statementBalanceMinor,
    minimumDueMinor = minimumDueMinor,
    feesMinor = feesMinor,
    interestMinor = interestMinor,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

  companion object {
    fun fromDomain(statement: CreditCardStatement) = CreditCardStatementEntity(
      id = statement.id,
      userId = statement.userId,
      cardId = statement.cardId,
      currencyCode = statement.currencyCode,
      statementDate = statement.statementDate,
      dueDate = statement.dueDate,
      statementBalanceMinor = statement.statementBalanceMinor,
      minimumDueMinor = statement.minimumDueMinor,
      feesMinor = statement.feesMinor,
      interestMinor = statement.interestMinor,
      notes = statement.notes,
      createdAt = statement.createdAt,
      updatedAt = statement.updatedAt,
    )
  }
}
