package com.example.domain.usecase

import com.example.domain.model.Budget
import com.example.domain.model.BudgetPeriodType
import com.example.domain.model.BudgetStatus
import com.example.domain.model.BudgetSummary
import com.example.domain.repository.BudgetRepository
import com.example.domain.repository.FinancialTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveBudgetsWithStatusUseCase @Inject constructor(
  private val budgetRepository: BudgetRepository,
  private val transactionRepository: FinancialTransactionRepository,
  private val calculationEngine: BudgetCalculationEngine,
) {
  operator fun invoke(userId: String): Flow<Pair<List<BudgetStatus>, Map<String, BudgetSummary>>> {
    return combine(
      budgetRepository.getBudgets(userId),
      transactionRepository.getTransactions(userId),
    ) { budgets, transactions ->
      val statuses = budgets.map { budget ->
        calculationEngine.calculateBudgetStatus(budget, transactions)
      }
      val summaries = calculationEngine.calculateSummaries(statuses)
      Pair(statuses, summaries)
    }
  }
}

@Singleton
class CreateBudgetUseCase @Inject constructor(
  private val budgetRepository: BudgetRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    name: String,
    category: String,
    amountMinor: Long,
    currencyCode: String,
    periodType: BudgetPeriodType,
    customStartDate: Long? = null,
    customEndDate: Long? = null,
    warningThresholdPercent: Int = 75,
  ): Result<Budget> {
    val (startDate, endDate) =
      if (periodType == BudgetPeriodType.MONTHLY) {
        getMonthlyDateRange()
      } else {
        requireNotNull(customStartDate) { "Start date is required for custom budget" }
        requireNotNull(customEndDate) { "End date is required for custom budget" }
        Pair(customStartDate, customEndDate)
      }

    val budget =
      Budget(
        userId = authenticatedUserId,
        name = name.trim(),
        category = category.trim(),
        amountMinor = amountMinor,
        currencyCode = currencyCode.uppercase().trim(),
        periodType = periodType,
        startDate = startDate,
        endDate = endDate,
        warningThresholdPercent = warningThresholdPercent,
      )

    return budgetRepository.createBudget(authenticatedUserId, budget)
  }

  private fun getMonthlyDateRange(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis

    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    val end = cal.timeInMillis

    return Pair(start, end)
  }
}

@Singleton
class UpdateBudgetUseCase @Inject constructor(
  private val budgetRepository: BudgetRepository,
) {
  suspend operator fun invoke(authenticatedUserId: String, budget: Budget): Result<Unit> {
    return budgetRepository.updateBudget(authenticatedUserId, budget)
  }
}

@Singleton
class DeleteBudgetUseCase @Inject constructor(
  private val budgetRepository: BudgetRepository,
) {
  suspend operator fun invoke(userId: String, budgetId: String): Result<Unit> {
    return budgetRepository.deleteBudget(userId, budgetId)
  }
}
