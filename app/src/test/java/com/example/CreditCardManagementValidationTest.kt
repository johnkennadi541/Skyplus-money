package com.example

import com.example.domain.model.CreditCard
import com.example.domain.model.CreditCardNetwork
import com.example.domain.model.CreditCardRewardEvent
import com.example.domain.model.CreditCardRewardEventType
import com.example.domain.model.CreditCardRewardValidation
import com.example.domain.model.CreditCardStatement
import com.example.domain.model.CreditCardStatementValidation
import com.example.domain.model.CreditCardValidation
import org.junit.Assert.assertThrows
import org.junit.Test

class CreditCardManagementValidationTest {

  private val card = CreditCard(
    userId = "uid_card_validation",
    accountId = "card_account",
    issuerName = "HDFC",
    network = CreditCardNetwork.VISA,
    last4 = "4321",
    creditLimitMinor = 100_000L,
    currencyCode = "INR",
  )

  @Test
  fun `card rejects negative annual fee and invalid reminder preferences`() {
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardValidation.requireValid(card.copy(annualFeeMinor = -1L), card.userId)
    }
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardValidation.requireValid(card.copy(annualFeeMonth = 13), card.userId)
    }
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardValidation.requireValid(card.copy(reminderDaysBefore = 31), card.userId)
    }
  }

  @Test
  fun `statement rejects due date before statement and minimum above balance`() {
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardStatementValidation.requireValid(
        statement(statementDate = 2_000L, dueDate = 1_000L, balance = 10_000L, minimum = 1_000L),
        card.userId,
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardStatementValidation.requireValid(
        statement(statementDate = 1_000L, dueDate = 2_000L, balance = 10_000L, minimum = 10_001L),
        card.userId,
      )
    }
  }

  @Test
  fun `reward events enforce earn and redeem point directions`() {
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardRewardValidation.requireValid(
        reward(eventType = CreditCardRewardEventType.EARN, points = -10L),
        card.userId,
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      CreditCardRewardValidation.requireValid(
        reward(eventType = CreditCardRewardEventType.REDEEM, points = 10L),
        card.userId,
      )
    }
  }

  private fun statement(
    statementDate: Long,
    dueDate: Long,
    balance: Long,
    minimum: Long,
  ) = CreditCardStatement(
    userId = card.userId,
    cardId = card.id,
    currencyCode = card.currencyCode,
    statementDate = statementDate,
    dueDate = dueDate,
    statementBalanceMinor = balance,
    minimumDueMinor = minimum,
  )

  private fun reward(eventType: CreditCardRewardEventType, points: Long) = CreditCardRewardEvent(
    userId = card.userId,
    cardId = card.id,
    eventDate = 1_000L,
    eventType = eventType,
    pointsDelta = points,
    currencyCode = card.currencyCode,
    description = "Test reward",
  )
}
