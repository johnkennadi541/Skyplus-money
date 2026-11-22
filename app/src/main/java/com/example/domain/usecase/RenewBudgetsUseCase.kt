package com.example.domain.usecase

import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.FinancialTransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Rolls every due recurring budget into the current month, for the signed-in user only.
 *
 * Safe to call on every screen open: renewal is idempotent, so once a budget has been moved its
 * window ends in the future and the next call does nothing.
 */
@Singleton
class RenewBudgetsUseCase
@Inject
constructor(
  private val budgetRepository: BudgetRepository,
  private val transactionRepository: FinancialTransactionRepository,
  private val renewalEngine: BudgetRenewalEngine,
) {

  /** Returns the number of budgets that were renewed. */
  suspend operator fun invoke(
    authenticatedUserId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Int> {
    if (authenticatedUserId.isBlank()) return Result.success(0)

    return runCatching {
      val budgets = budgetRepository.getBudgets(authenticatedUserId).first()
      if (budgets.isEmpty()) return@runCatching 0

      val transactions = transactionRepository.getTransactions(authenticatedUserId).first()

      var renewed = 0
      for (budget in budgets) {
        val plan = renewalEngine.plan(budget, transactions, nowMillis)
        if (plan is BudgetRenewalPlan.Due) {
          budgetRepository
            .applyRenewal(
              authenticatedUserId = authenticatedUserId,
              budgetId = plan.budgetId,
              closedPeriods = plan.closedPeriods,
              nextStart = plan.nextStart,
              nextEnd = plan.nextEnd,
              renewedAt = plan.renewedAt,
            )
            .getOrThrow()
          renewed++
        }
      }
      renewed
    }
  }
}
