package com.example.data.di

import com.example.data.repository.ConsentRepositoryImpl
import com.example.data.repository.UserPreferencesRepositoryImpl
import com.example.domain.repository.ConsentRepository
import com.example.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

  @Binds
  @Singleton
  abstract fun bindUserPreferencesRepository(
    impl: UserPreferencesRepositoryImpl
  ): UserPreferencesRepository

  @Binds
  @Singleton
  abstract fun bindConsentRepository(
    impl: ConsentRepositoryImpl
  ): ConsentRepository

  @Binds
  @Singleton
  abstract fun bindFinancialAccountRepository(
    impl: com.example.data.repository.FinancialAccountRepositoryImpl
  ): com.example.domain.repository.FinancialAccountRepository

  @Binds
  @Singleton
  abstract fun bindFinancialTransactionRepository(
    impl: com.example.data.repository.FinancialTransactionRepositoryImpl
  ): com.example.domain.repository.FinancialTransactionRepository

  @Binds
  @Singleton
  abstract fun bindSmartDetectionRepository(
    impl: com.example.data.repository.SmartDetectionRepositoryImpl
  ): com.example.domain.repository.SmartDetectionRepository

  @Binds
  @Singleton
  abstract fun bindBudgetRepository(
    impl: com.example.data.repository.BudgetRepositoryImpl
  ): com.example.domain.repository.BudgetRepository

  @Binds
  @Singleton
  abstract fun bindLoanRepository(
    impl: com.example.data.repository.LoanRepositoryImpl
  ): com.example.domain.repository.LoanRepository

  @Binds
  @Singleton
  abstract fun bindMarketPriceDataSource(
    impl: com.example.data.remote.AmfiMfApiMarketPriceDataSource
  ): com.example.domain.repository.MarketPriceDataSource

  @Binds
  @Singleton
  abstract fun bindInvestmentRepository(
    impl: com.example.data.repository.InvestmentRepositoryImpl
  ): com.example.domain.repository.InvestmentRepository

  @Binds
  @Singleton
  abstract fun bindCreditCardRepository(
    impl: com.example.data.repository.CreditCardRepositoryImpl
  ): com.example.domain.repository.CreditCardRepository

  @Binds
  @Singleton
  abstract fun bindSavingsGoalRepository(
    impl: com.example.data.repository.SavingsGoalRepositoryImpl
  ): com.example.domain.repository.SavingsGoalRepository

  @Binds
  @Singleton
  abstract fun bindShoppingRepository(
    impl: com.example.data.repository.ShoppingRepositoryImpl
  ): com.example.domain.repository.ShoppingRepository

  @Binds
  @Singleton
  abstract fun bindShoppingExtendedRepository(
    impl: com.example.data.repository.ShoppingExtendedRepositoryImpl
  ): com.example.domain.repository.ShoppingExtendedRepository
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object NetworkProvidersModule {

  @dagger.Provides
  @Singleton
  fun provideOkHttpCallFactory(): okhttp3.Call.Factory =
    okhttp3.OkHttpClient.Builder()
      .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
      .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
      .build()
}
