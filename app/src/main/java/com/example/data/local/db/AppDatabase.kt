package com.example.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.db.dao.AccountMappingRuleDao
import com.example.data.local.db.dao.BudgetDao
import com.example.data.local.db.dao.CreditCardDao
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.dao.InvestmentDao
import com.example.data.local.db.dao.LoanDao
import com.example.data.local.db.dao.ShoppingDao
import com.example.data.local.db.dao.TransactionDetectionEventDao
import com.example.data.local.db.entity.AccountMappingRuleEntity
import com.example.data.local.db.entity.BudgetEntity
import com.example.data.local.db.entity.CreditCardEntity
import com.example.data.local.db.entity.BudgetPeriodEntity
import com.example.data.local.db.entity.FinancialAccountEntity
import com.example.data.local.db.entity.FinancialTransactionEntity
import com.example.data.local.db.entity.InvestmentHoldingEntity
import com.example.data.local.db.entity.InvestmentPriceHistoryEntity
import com.example.data.local.db.entity.InvestmentTransactionEntity
import com.example.data.local.db.entity.LoanEntity
import com.example.data.local.db.entity.LoanPaymentEntity
import com.example.data.local.db.entity.ShoppingItemEntity
import com.example.data.local.db.entity.ShoppingListEntity
import com.example.data.local.db.entity.ShoppingStorePriceEntity
import com.example.data.local.db.entity.TransactionDetectionEventEntity

@Database(
  entities = [
    FinancialAccountEntity::class,
    FinancialTransactionEntity::class,
    TransactionDetectionEventEntity::class,
    AccountMappingRuleEntity::class,
    BudgetEntity::class,
    BudgetPeriodEntity::class,
    CreditCardEntity::class,
    LoanEntity::class,
    LoanPaymentEntity::class,
    InvestmentHoldingEntity::class,
    InvestmentTransactionEntity::class,
    InvestmentPriceHistoryEntity::class,
    ShoppingListEntity::class,
    ShoppingItemEntity::class,
    ShoppingStorePriceEntity::class,
  ],
  version = 8,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun financialAccountDao(): FinancialAccountDao
  abstract fun financialTransactionDao(): FinancialTransactionDao
  abstract fun transactionDetectionEventDao(): TransactionDetectionEventDao
  abstract fun accountMappingRuleDao(): AccountMappingRuleDao
  abstract fun budgetDao(): BudgetDao
  abstract fun creditCardDao(): CreditCardDao
  abstract fun loanDao(): LoanDao
  abstract fun investmentDao(): InvestmentDao
  abstract fun shoppingDao(): ShoppingDao
}
