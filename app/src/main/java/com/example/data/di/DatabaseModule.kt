package com.example.data.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.BuildConfig
import com.example.core.database.DatabaseContract
import com.example.core.security.DatabasePassphraseProvider
import com.example.core.security.DatabaseSecurityException
import com.example.core.security.DatabaseSecurityMonitor
import com.example.core.security.PassphraseFailureReason
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.DatabaseMigrations
import com.example.data.local.db.dao.AccountMappingRuleDao
import com.example.data.local.db.dao.BudgetDao
import com.example.data.local.db.dao.CreditCardDao
import com.example.data.local.db.dao.FinancialAccountDao
import com.example.data.local.db.dao.FinancialTransactionDao
import com.example.data.local.db.dao.InvestmentDao
import com.example.data.local.db.dao.LoanDao
import com.example.data.local.db.dao.ShoppingDao
import com.example.data.local.db.dao.TransactionDetectionEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabasePassphraseProvider(
    @ApplicationContext context: Context,
  ): DatabasePassphraseProvider = DatabasePassphraseProvider(context)

  @Provides
  @Singleton
  fun provideAppDatabase(
    @ApplicationContext context: Context,
    passphraseProvider: DatabasePassphraseProvider,
    securityMonitor: DatabaseSecurityMonitor,
  ): AppDatabase {
    val factoryHolder = RecoverableEncryptedOpenHelperFactory()

    fun attemptEncryptedInit(): Boolean {
      return try {
        val factory =
          createEncryptedFactoryOrThrow(
            context = context,
            passphraseProvider = passphraseProvider,
            isReleaseBuild = !BuildConfig.DEBUG,
            isRobolectricRunner = isRobolectricTestEnvironment(),
          )
        factoryHolder.delegate = factory
        securityMonitor.clearIssue()
        true
      } catch (securityError: DatabaseSecurityException) {
        factoryHolder.lastError = securityError
        securityMonitor.reportIssue(securityError)
        false
      }
    }

    securityMonitor.registerRetryHandler(::attemptEncryptedInit)
    attemptEncryptedInit()

    val builder =
      Room.databaseBuilder(context, AppDatabase::class.java, DatabaseContract.DATABASE_NAME)
        .addMigrations(
          DatabaseMigrations.MIGRATION_1_2,
          DatabaseMigrations.MIGRATION_2_3,
          DatabaseMigrations.MIGRATION_3_4,
          DatabaseMigrations.MIGRATION_4_5,
          DatabaseMigrations.MIGRATION_5_6,
          DatabaseMigrations.MIGRATION_6_7,
          DatabaseMigrations.MIGRATION_7_8,
        )

    if (factoryHolder.delegate != null || factoryHolder.lastError != null) {
      builder.openHelperFactory(factoryHolder)
    }

    return builder.build()
  }

  /**
   * Builds [AppDatabase] enforcing fail-closed SQLCipher encryption.
   * Plaintext SQLite fallback is strictly forbidden in Release builds and on real Android devices;
   * it can activate ONLY inside local debug Robolectric unit test runners when native SQLCipher
   * binaries are absent.
   */
  internal fun buildAppDatabase(
    context: Context,
    passphraseProvider: DatabasePassphraseProvider,
    isReleaseBuild: Boolean = !BuildConfig.DEBUG,
    isRobolectricRunner: Boolean = isRobolectricTestEnvironment(),
    nativeLibsLoader: (Context) -> Unit = { net.sqlcipher.database.SQLiteDatabase.loadLibs(it) },
  ): AppDatabase {
    val builder =
      Room.databaseBuilder(context, AppDatabase::class.java, DatabaseContract.DATABASE_NAME)
        .addMigrations(
          DatabaseMigrations.MIGRATION_1_2,
          DatabaseMigrations.MIGRATION_2_3,
          DatabaseMigrations.MIGRATION_3_4,
          DatabaseMigrations.MIGRATION_4_5,
          DatabaseMigrations.MIGRATION_5_6,
          DatabaseMigrations.MIGRATION_6_7,
          DatabaseMigrations.MIGRATION_7_8,
        )

    val encryptedFactory =
      createEncryptedFactoryOrThrow(
        context = context,
        passphraseProvider = passphraseProvider,
        isReleaseBuild = isReleaseBuild,
        isRobolectricRunner = isRobolectricRunner,
        nativeLibsLoader = nativeLibsLoader,
      )

    if (encryptedFactory != null) {
      builder.openHelperFactory(encryptedFactory)
    }
    return builder.build()
  }

  internal fun createEncryptedFactoryOrThrow(
    context: Context,
    passphraseProvider: DatabasePassphraseProvider,
    isReleaseBuild: Boolean = !BuildConfig.DEBUG,
    isRobolectricRunner: Boolean = isRobolectricTestEnvironment(),
    nativeLibsLoader: (Context) -> Unit = { net.sqlcipher.database.SQLiteDatabase.loadLibs(it) },
  ): SupportSQLiteOpenHelper.Factory? {
    return try {
      nativeLibsLoader(context)
      val passphrase = passphraseProvider.getOrCreatePassphrase()
      SupportFactory(passphrase)
    } catch (t: Throwable) {
      val allowTestFallback = !isReleaseBuild && isRobolectricRunner
      if (allowTestFallback) {
        null
      } else {
        throw (t as? DatabaseSecurityException)
          ?: DatabaseSecurityException(
            reason = PassphraseFailureReason.SQLCIPHER_INIT_FAILED,
            isRecoverable = true,
            message = "SQLCipher encrypted database initialization failed; plaintext fallback is prohibited.",
            cause = t,
          )
      }
    }
  }

  internal fun isRobolectricTestEnvironment(): Boolean {
    if (!BuildConfig.DEBUG) return false
    if (!"robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) return false
    return runCatching {
      Class.forName("org.robolectric.Robolectric")
      true
    }.getOrDefault(false)
  }

  private class RecoverableEncryptedOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    @Volatile var delegate: SupportSQLiteOpenHelper.Factory? = null
    @Volatile var lastError: DatabaseSecurityException? = null

    override fun create(
      configuration: SupportSQLiteOpenHelper.Configuration,
    ): SupportSQLiteOpenHelper {
      val currentDelegate = delegate
      if (currentDelegate != null) {
        return currentDelegate.create(configuration)
      }
      throw lastError
        ?: DatabaseSecurityException(
          reason = PassphraseFailureReason.SQLCIPHER_INIT_FAILED,
          isRecoverable = true,
          message = "Encrypted database is locked because encryption initialization failed.",
        )
    }
  }

  @Provides
  fun provideFinancialAccountDao(database: AppDatabase): FinancialAccountDao =
    database.financialAccountDao()

  @Provides
  fun provideFinancialTransactionDao(database: AppDatabase): FinancialTransactionDao =
    database.financialTransactionDao()

  @Provides
  fun provideTransactionDetectionEventDao(database: AppDatabase): TransactionDetectionEventDao =
    database.transactionDetectionEventDao()

  @Provides
  fun provideAccountMappingRuleDao(database: AppDatabase): AccountMappingRuleDao =
    database.accountMappingRuleDao()

  @Provides
  fun provideBudgetDao(database: AppDatabase): BudgetDao =
    database.budgetDao()

  @Provides
  fun provideCreditCardDao(database: AppDatabase): CreditCardDao =
    database.creditCardDao()

  @Provides
  fun provideLoanDao(database: AppDatabase): LoanDao =
    database.loanDao()

  @Provides
  fun provideInvestmentDao(database: AppDatabase): InvestmentDao =
    database.investmentDao()

  @Provides
  fun provideShoppingDao(database: AppDatabase): ShoppingDao =
    database.shoppingDao()
}
