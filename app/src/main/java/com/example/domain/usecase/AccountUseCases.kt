package com.example.domain.usecase

import com.example.domain.model.FinancialAccount
import com.example.domain.repository.FinancialAccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAccountsUseCase @Inject constructor(
  private val repository: FinancialAccountRepository,
) {
  operator fun invoke(userId: String): Flow<List<FinancialAccount>> =
    repository.getAccounts(userId)
}

class CreateAccountUseCase @Inject constructor(
  private val repository: FinancialAccountRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    account: FinancialAccount,
  ): Result<FinancialAccount> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(account.name.isNotBlank()) { "Account name must not be blank" }
    require(account.currencyCode.length == 3) { "Invalid ISO 4217 currency code" }
    return repository.createAccount(authenticatedUserId, account)
  }
}

class UpdateAccountUseCase @Inject constructor(
  private val repository: FinancialAccountRepository,
) {
  suspend operator fun invoke(
    authenticatedUserId: String,
    account: FinancialAccount,
  ): Result<Unit> {
    require(authenticatedUserId.isNotBlank()) { "Authenticated user ID is required" }
    require(account.name.isNotBlank()) { "Account name must not be blank" }
    return repository.updateAccount(authenticatedUserId, account)
  }
}

class DeleteAccountUseCase @Inject constructor(
  private val repository: FinancialAccountRepository,
) {
  suspend operator fun invoke(
    userId: String,
    accountId: String,
    deleteLinkedTransactions: Boolean = false,
  ): Result<Unit> = repository.deleteAccount(userId, accountId, deleteLinkedTransactions)
}

class SetAccountActiveUseCase @Inject constructor(
  private val repository: FinancialAccountRepository,
) {
  suspend operator fun invoke(
    userId: String,
    accountId: String,
    isActive: Boolean,
  ): Result<Unit> = repository.setAccountActive(userId, accountId, isActive)
}
