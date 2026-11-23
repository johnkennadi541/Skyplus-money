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

/**
 * Contributions deliberately carry **no database-level foreign key** to `savings_goals`.
 *
 * SQLite cannot add a foreign key to an existing table, so adding one would require a full
 * create/copy/drop/rename rebuild of a table holding user data inside a migration. If a single
 * orphan row existed the copy would abort and the encrypted database would fail to open, which the
 * fail-closed SQLCipher path turns into an unusable app. Making the rebuild always succeed would
 * mean deleting orphans, which is silent data loss.
 *
 * Integrity is instead guaranteed by [com.example.data.repository.SavingsGoalRepositoryImpl], which
 * deletes a goal's contributions inside the same transaction as the goal itself, and that path is
 * covered by tests. The foreign key would only add protection against direct DAO writes that bypass
 * the repository, which do not exist today.
 */
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
