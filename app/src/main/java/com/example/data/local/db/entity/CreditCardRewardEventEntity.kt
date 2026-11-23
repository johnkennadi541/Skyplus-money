package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.CreditCardRewardEvent
import com.example.domain.model.CreditCardRewardEventType

@Entity(
  tableName = "credit_card_reward_events",
  foreignKeys = [
    ForeignKey(
      entity = CreditCardEntity::class,
      parentColumns = ["id"],
      childColumns = ["cardId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["cardId"]),
    Index(value = ["userId", "cardId", "eventDate"]),
    Index(value = ["userId", "cardId"]),
  ],
)
data class CreditCardRewardEventEntity(
  @PrimaryKey val id: String,
  val userId: String,
  val cardId: String,
  val eventDate: Long,
  val eventType: String,
  val pointsDelta: Long,
  val cashValueMinor: Long,
  val currencyCode: String,
  val description: String,
  val createdAt: Long,
) {
  fun toDomain() = CreditCardRewardEvent(
    id = id,
    userId = userId,
    cardId = cardId,
    eventDate = eventDate,
    eventType = runCatching { CreditCardRewardEventType.valueOf(eventType) }
      .getOrDefault(CreditCardRewardEventType.ADJUSTMENT),
    pointsDelta = pointsDelta,
    cashValueMinor = cashValueMinor,
    currencyCode = currencyCode,
    description = description,
    createdAt = createdAt,
  )

  companion object {
    fun fromDomain(event: CreditCardRewardEvent) = CreditCardRewardEventEntity(
      id = event.id,
      userId = event.userId,
      cardId = event.cardId,
      eventDate = event.eventDate,
      eventType = event.eventType.name,
      pointsDelta = event.pointsDelta,
      cashValueMinor = event.cashValueMinor,
      currencyCode = event.currencyCode,
      description = event.description,
      createdAt = event.createdAt,
    )
  }
}
