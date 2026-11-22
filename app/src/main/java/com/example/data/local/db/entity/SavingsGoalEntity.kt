package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SavingsContribution
import com.example.domain.model.SavingsGoal

@Entity(
  tableName = "savings_goals",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "currencyCode"]),
  ],
)
data class SavingsGoalEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val name: String,
  val currencyCode: String,
  val targetMinor: Long,
  val deadlineMillis: Long?,
  val note: String?,
  val createdAt: Long,
  val updatedAt: Long,
) {
  fun toDomain(): SavingsGoal =
    SavingsGoal(
      id = id,
      userId = userId,
      name = name,
      currencyCode = currencyCode,
      targetMinor = targetMinor,
      deadlineMillis = deadlineMillis,
      note = note,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )

  companion object {
    fun fromDomain(goal: SavingsGoal): SavingsGoalEntity =
      SavingsGoalEntity(
        id = goal.id,
        userId = goal.userId,
        name = goal.name,
        currencyCode = goal.currencyCode,
        targetMinor = goal.targetMinor,
        deadlineMillis = goal.deadlineMillis,
        note = goal.note,
        createdAt = goal.createdAt,
        updatedAt = goal.updatedAt,
      )
  }
}

@Entity(
  tableName = "savings_contributions",
  indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "goalId"]),
    Index(value = ["userId", "goalId", "contributedAt"]),
  ],
)
data class SavingsContributionEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val goalId: String,
  val amountMinor: Long,
  val currencyCode: String,
  val contributedAt: Long,
  val note: String?,
  val createdAt: Long,
) {
  fun toDomain(): SavingsContribution =
    SavingsContribution(
      id = id,
      userId = userId,
      goalId = goalId,
      amountMinor = amountMinor,
      currencyCode = currencyCode,
      contributedAt = contributedAt,
      note = note,
      createdAt = createdAt,
    )

  companion object {
    fun fromDomain(contribution: SavingsContribution): SavingsContributionEntity =
      SavingsContributionEntity(
        id = contribution.id,
        userId = contribution.userId,
        goalId = contribution.goalId,
        amountMinor = contribution.amountMinor,
        currencyCode = contribution.currencyCode,
        contributedAt = contribution.contributedAt,
        note = contribution.note,
        createdAt = contribution.createdAt,
      )
  }
}
