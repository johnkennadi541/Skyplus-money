package com.example.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit non-destructive Room migrations for SKYPLUS MONEY.
 * Destructive fallbacks are strictly prohibited to protect user financial ledgers.
 */
object DatabaseMigrations {
  val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE financial_transactions ADD COLUMN normalizedMerchant TEXT")
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_financial_transactions_userId_normalizedMerchant ON financial_transactions(userId, normalizedMerchant)"
        )
      }
    }

  val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS budgets (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            amountMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            periodType TEXT NOT NULL,
            startDate INTEGER NOT NULL,
            endDate INTEGER NOT NULL,
            warningThresholdPercent INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budgets_userId_category ON budgets(userId, category)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budgets_userId_currencyCode ON budgets(userId, currencyCode)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budgets_userId_startDate_endDate ON budgets(userId, startDate, endDate)"
        )
      }
    }

  val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS loans (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            lenderName TEXT NOT NULL,
            loanType TEXT NOT NULL,
            principalMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            annualInterestRatePercent TEXT NOT NULL,
            tenureMonths INTEGER NOT NULL,
            startDate INTEGER NOT NULL,
            nextDueDate INTEGER NOT NULL,
            monthlyEmiMinor INTEGER NOT NULL,
            totalPayableMinor INTEGER NOT NULL,
            totalInterestMinor INTEGER NOT NULL,
            amountPaidMinor INTEGER NOT NULL,
            outstandingPrincipalMinor INTEGER NOT NULL,
            status TEXT NOT NULL,
            notes TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_userId ON loans(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_userId_status ON loans(userId, status)")
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_loans_userId_currencyCode ON loans(userId, currencyCode)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_loans_userId_nextDueDate ON loans(userId, nextDueDate)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS loan_payments (
            id TEXT NOT NULL PRIMARY KEY,
            loanId TEXT NOT NULL,
            userId TEXT NOT NULL,
            paymentDate INTEGER NOT NULL,
            amountMinor INTEGER NOT NULL,
            principalComponentMinor INTEGER NOT NULL,
            interestComponentMinor INTEGER NOT NULL,
            remainingBalanceMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            status TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            notes TEXT,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(loanId) REFERENCES loans(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_payments_loanId ON loan_payments(loanId)")
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_loan_payments_userId_loanId_paymentDate ON loan_payments(userId, loanId, paymentDate)"
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS index_loan_payments_userId_loanId_idempotencyKey ON loan_payments(userId, loanId, idempotencyKey)"
        )
      }
    }

  val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS investment_holdings (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            symbolOrTicker TEXT,
            isin TEXT,
            brokerOrPlatform TEXT,
            currencyCode TEXT NOT NULL,
            purchaseDate INTEGER NOT NULL,
            quantityDecimal TEXT NOT NULL,
            totalInvestedMinor INTEGER NOT NULL,
            totalBuyCostMinor INTEGER NOT NULL,
            realizedPnlMinor INTEGER NOT NULL,
            totalChargesMinor INTEGER NOT NULL,
            currentMarketPriceDecimal TEXT NOT NULL,
            currentValuationMinor INTEGER NOT NULL,
            lastPriceUpdateDate INTEGER NOT NULL,
            lastPriceSource TEXT NOT NULL,
            lastPriceProviderLabel TEXT,
            autoPriceUpdateEnabled INTEGER NOT NULL,
            notes TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_holdings_userId ON investment_holdings(userId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_holdings_userId_category ON investment_holdings(userId, category)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_holdings_userId_currencyCode ON investment_holdings(userId, currencyCode)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS investment_transactions (
            id TEXT NOT NULL PRIMARY KEY,
            holdingId TEXT NOT NULL,
            userId TEXT NOT NULL,
            type TEXT NOT NULL,
            transactionDate INTEGER NOT NULL,
            quantityDecimal TEXT NOT NULL,
            unitPriceDecimal TEXT NOT NULL,
            grossAmountMinor INTEGER NOT NULL,
            chargesMinor INTEGER NOT NULL,
            netAmountMinor INTEGER NOT NULL,
            realizedPnlMinor INTEGER NOT NULL,
            costBasisAllocatedMinor INTEGER NOT NULL,
            notes TEXT,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(holdingId) REFERENCES investment_holdings(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_transactions_holdingId ON investment_transactions(holdingId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_transactions_userId_holdingId_transactionDate ON investment_transactions(userId, holdingId, transactionDate)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS investment_price_history (
            id TEXT NOT NULL PRIMARY KEY,
            holdingId TEXT NOT NULL,
            userId TEXT NOT NULL,
            priceDate INTEGER NOT NULL,
            unitPriceDecimal TEXT NOT NULL,
            valuationMinor INTEGER NOT NULL,
            totalInvestedMinor INTEGER NOT NULL,
            realizedPnlMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            source TEXT NOT NULL,
            providerLabel TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(holdingId) REFERENCES investment_holdings(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_price_history_holdingId ON investment_price_history(holdingId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_price_history_userId_holdingId_priceDate ON investment_price_history(userId, holdingId, priceDate)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_investment_price_history_userId_currencyCode_priceDate ON investment_price_history(userId, currencyCode, priceDate)"
        )
      }
    }

  val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS shopping_lists (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            currencyCode TEXT NOT NULL,
            budgetMinor INTEGER,
            warningThresholdPercent INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_lists_userId ON shopping_lists(userId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_lists_userId_name ON shopping_lists(userId, name)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_lists_userId_currencyCode ON shopping_lists(userId, currencyCode)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS shopping_items (
            id TEXT NOT NULL PRIMARY KEY,
            listId TEXT NOT NULL,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            quantityDecimal TEXT NOT NULL,
            unit TEXT NOT NULL,
            estimatedUnitPriceMinor INTEGER NOT NULL,
            estimatedTotalMinor INTEGER NOT NULL,
            storeName TEXT,
            notes TEXT,
            isPurchased INTEGER NOT NULL,
            actualUnitPriceMinor INTEGER,
            actualTotalMinor INTEGER,
            purchasedAt INTEGER,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            FOREIGN KEY(listId) REFERENCES shopping_lists(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_items_listId ON shopping_items(listId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_items_userId_listId ON shopping_items(userId, listId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_items_userId_isPurchased ON shopping_items(userId, isPurchased)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS shopping_store_prices (
            id TEXT NOT NULL PRIMARY KEY,
            itemId TEXT NOT NULL,
            listId TEXT NOT NULL,
            userId TEXT NOT NULL,
            storeName TEXT NOT NULL,
            unitPriceMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            recordedAt INTEGER NOT NULL,
            notes TEXT,
            FOREIGN KEY(itemId) REFERENCES shopping_items(id) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_store_prices_itemId ON shopping_store_prices(itemId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_store_prices_userId_itemId_recordedAt ON shopping_store_prices(userId, itemId, recordedAt)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_shopping_store_prices_userId_listId ON shopping_store_prices(userId, listId)"
        )
      }
    }
  /**
   * Stage 3 PR 1 — budget renewal.
   *
   * Additive only: one new archive table plus two columns on `budgets`. No row is inserted,
   * deleted, or changed in any other column, so every existing budget and transaction survives.
   *
   * `isRecurring` is added with a default of 0 and then switched on ONLY for budgets that are
   * genuinely eligible: a MONTHLY period type (case-insensitive, trimmed), a positive limit and a
   * sane window. Every CUSTOM, zero-limit or malformed budget stays non-recurring.
   */
  val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS budget_periods (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            budgetId TEXT NOT NULL,
            category TEXT NOT NULL,
            currencyCode TEXT NOT NULL,
            periodType TEXT NOT NULL,
            periodStart INTEGER NOT NULL,
            periodEnd INTEGER NOT NULL,
            allocatedMinor INTEGER NOT NULL,
            spentMinor INTEGER NOT NULL,
            remainingMinor INTEGER NOT NULL,
            alertStatus TEXT NOT NULL,
            closedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budget_periods_userId ON budget_periods(userId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budget_periods_userId_budgetId ON budget_periods(userId, budgetId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_budget_periods_userId_periodEnd ON budget_periods(userId, periodEnd)"
        )

        db.execSQL(
          "ALTER TABLE budgets ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
          "ALTER TABLE budgets ADD COLUMN lastRenewedAt INTEGER NOT NULL DEFAULT 0"
        )

        db.execSQL(
          """
          UPDATE budgets
          SET isRecurring = 1
          WHERE upper(trim(periodType)) = 'MONTHLY'
            AND amountMinor > 0
            AND endDate > startDate
          """.trimIndent()
        )
      }
    }
  /**
   * Stage 3 PR 2 — credit cards.
   *
   * Additive only: one new table with a unique index on accountId, plus a backfill that gives every
   * existing CREDIT_CARD account a card record with safe defaults. No financial account, transaction
   * or balance is created, updated or deleted here.
   */
  val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS credit_cards (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            accountId TEXT NOT NULL,
            issuerName TEXT NOT NULL,
            network TEXT NOT NULL,
            last4 TEXT NOT NULL,
            creditLimitMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            billingCycleDay INTEGER NOT NULL,
            paymentDueDayOffset INTEGER NOT NULL,
            aprPercent TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS index_credit_cards_accountId ON credit_cards(accountId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_credit_cards_userId ON credit_cards(userId)"
        )

        // Backfill: one card per existing credit-card account, safe defaults, no data touched.
        db.execSQL(
          """
          INSERT OR IGNORE INTO credit_cards
            (id, userId, accountId, issuerName, network, last4, creditLimitMinor, currencyCode,
             billingCycleDay, paymentDueDayOffset, aprPercent, isActive, createdAt, updatedAt)
          SELECT
            lower(hex(randomblob(16))),
            userId,
            id,
            coalesce(institutionName, name),
            'OTHER',
            coalesce(accountNumberLast4, ''),
            0,
            currencyCode,
            1,
            20,
            '0',
            isActive,
            createdAt,
            updatedAt
          FROM financial_accounts
          WHERE accountType = 'CREDIT_CARD'
          """.trimIndent()
        )
      }
    }
  /**
   * Stage 3 PR 3 — savings goals.
   *
   * Additive only: two new tables and their indices. No existing account, transaction, budget,
   * credit-card, loan, investment or shopping row is touched.
   */
  val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS savings_goals (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            name TEXT NOT NULL,
            currencyCode TEXT NOT NULL,
            targetMinor INTEGER NOT NULL,
            deadlineMillis INTEGER,
            note TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_userId ON savings_goals(userId)")
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_savings_goals_userId_currencyCode ON savings_goals(userId, currencyCode)"
        )

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS savings_contributions (
            id TEXT NOT NULL PRIMARY KEY,
            userId TEXT NOT NULL,
            goalId TEXT NOT NULL,
            amountMinor INTEGER NOT NULL,
            currencyCode TEXT NOT NULL,
            contributedAt INTEGER NOT NULL,
            note TEXT,
            createdAt INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_savings_contributions_userId ON savings_contributions(userId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_savings_contributions_userId_goalId ON savings_contributions(userId, goalId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_savings_contributions_userId_goalId_contributedAt ON savings_contributions(userId, goalId, contributedAt)"
        )
      }
    }
  /**
   * Credit-card archive state. Additive only: existing card rows and all linked ledger data remain
   * intact; existing cards default to visible/unarchived.
   */
  val MIGRATION_9_10: Migration =
    object : Migration(9, 10) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE credit_cards ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
      }
    }

  /**
   * GOAL-01 — preserve savings goal and contribution history.
   *
   * Additive only. Existing goals and contributions default to active/not voided;
   * no financial or historical row is deleted or rewritten.
   */
  val MIGRATION_10_11: Migration =
    object : Migration(10, 11) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE savings_goals ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE savings_contributions ADD COLUMN isVoided INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE savings_contributions ADD COLUMN voidedAt INTEGER DEFAULT NULL")
      }
    }

}