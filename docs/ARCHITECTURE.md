# SKYPLUS MONEY — Architecture Documentation

## 1. Module & Package Layout

While `applicationId` is configured as `com.skyplus.money` in `app/build.gradle.kts`, `namespace` remains `com.example` to keep generated `R` and `BuildConfig` references aligned with the Android toolchain template.

```text
app/src/main/java/com/example/
├── SkyplusMoneyApp.kt                  # @HiltAndroidApp application entry
├── MainActivity.kt                     # @AndroidEntryPoint, Edge-to-Edge host -> SkyplusAuthGateRoot + Phase 2 VMs
├── core/
│   ├── common/                         # DispatcherProvider, AppResult, AppFoundationConfig, DI
│   ├── database/                       # DatabaseContract (v2, SQLCipher Active)
│   ├── designsystem/                   # OceanColors, OceanTypography, OceanShapes, OceanTheme, components
│   ├── network/                        # ConnectivityContract
│   ├── security/                       # SecurityPolicy, DatabasePassphraseProvider (Android Keystore AES-GCM)
│   └── sync/                           # SyncContract (Cloud sync disabled offline-first)
├── data/
│   ├── di/                             # DataModule, AuthModule, DatabaseModule (Room + SQLCipher)
│   ├── local/
│   │   ├── db/                         # AppDatabase (v2), DatabaseMigrations (MIGRATION_1_2), DAOs, Entities
│   │   └── *PreferencesDataSource.kt   # DataStore Preferences (Theme, Consent)
│   ├── remote/                         # GoogleCredentialClient, FirebaseAuthDataSource, ExternalIntegrationStatus
│   └── repository/                     # UserPreferences, Auth, Consent, FinancialAccount, FinancialTransaction, SmartDetection Impl
├── domain/
│   ├── model/                          # Money, FinancialAccount, FinancialTransaction, DetectionModels, AuthModels, ConsentRecord
│   ├── repository/                     # Domain repository interfaces (UID-isolated)
│   └── usecase/                        # FormatMoneyUseCase, AccountUseCases, TransactionUseCases, SmartDetectionUseCases, Auth/Consent UseCases
├── service/
│   ├── NotificationPermissionHelper.kt # System Notification Access inspection & settings intent
│   └── SkyplusNotificationListenerService.kt # On-device NotificationListenerService (@AndroidEntryPoint)
├── feature/
│   ├── authentication/                 # AuthViewModel, GoogleAuthScreen, AuthLoadingScreen, AuthFoundationScreen
│   ├── consent/                        # LegalConsentScreen, LegalConsentPreviewScreen (Phase 1D)
│   ├── dashboard/                      # HomeViewModel, HomeScreen (Multi-currency balances, auto-saved banner, shortcuts)
│   ├── accounts/                       # AccountsViewModel, AccountsScreen, AccountDetailScreen
│   ├── transactions/                   # TransactionsViewModel, TransactionsScreen, IncomeManagementScreen, ExpenseManagementScreen
│   ├── smartdetection/
│   │   ├── engine/                     # SmartTransactionParser, DeduplicationEngine
│   │   └── *.kt                        # SmartDetectionViewModel, Settings, Notification Setup, SMS Policy Status, Review Queue, Correction Dialog, Mapping Rules, Audit History
│   ├── budgets/                        # BudgetsScreen (Phase 3 placeholder)
│   ├── investments/                    # InvestmentsScreen (Phase 4 placeholder)
│   ├── reports/                        # ReportsScreen (Phase 4 placeholder)
│   └── settings/                       # MoreScreen, SettingsScreen, SettingsViewModel
└── navigation/
    ├── SkyplusDestination.kt           # @Serializable type-safe routes (incl. Phase 2 Accounts, AccountDetail, Income/Expense, SmartDetection subroutes)
    └── SkyplusAppShell.kt              # SkyplusAuthGateRoot + Adaptive NavigationBar / NavigationRail + NavHost
```

## 2. Phase 1 Auth & Consent Flow (Preserved)
1. `MainActivity` observes `AuthViewModel.uiState` alongside `AccountsViewModel`, `TransactionsViewModel`, and `SmartDetectionViewModel` and renders `SkyplusAuthGateRoot`.
2. Unauthenticated users see `GoogleAuthScreen` (no Guest Mode). Authenticated users without current legal consent (`v1.0.0`) see `LegalConsentScreen` keyed by `user.uid`.
3. Only authenticated + consented users enter `SkyplusAppShell`.

## 3. Phase 2 Financial & Smart Detection Architecture
- **Atomic Financial Ledger:** `FinancialAccountRepositoryImpl` and `FinancialTransactionRepositoryImpl` perform multi-step balance mutations, internal transfers (`TRANSFER_OUT_CATEGORY` + `TRANSFER_IN_CATEGORY`), and transaction edits/deletions inside `AppDatabase.withTransaction` using `Long` minor units and `BigDecimal`. Cross-currency transfers explicitly require a converted destination amount and reject implicit 1:1 mixing.
- **On-Device Smart Detection Pipeline:**
  1. `SkyplusNotificationListenerService` validates authenticated UID, notification toggle, and package allowlist.
  2. `SmartTransactionParser` filters sensitive OTP/PIN/CVV notifications immediately (`return null`), parses ISO 4217 multi-currency amounts with `BigDecimal`, classifies `EXPENSE`/`INCOME`/`REFUND`/`REVERSAL`/`PENDING`/`FAILED`/`TRANSFER`, extracts account last-4, reference ID, and merchant, and scores confidence (`HIGH` >= 0.80 completed, `MEDIUM` >= 0.50, `LOW` < 0.50).
  3. `DeduplicationEngine` + `SmartDetectionRepositoryImpl` checks SHA-256 fingerprint, reference ID across events & transactions, and a 10-minute amount/currency/merchant window inside a single atomic Room transaction to prevent duplicate balance mutations.
  4. **Read-only by design:** every new, non-duplicate notification event enters the `IN_REVIEW` queue. Parsing and account mapping never create a financial transaction or change a balance. Only the authenticated user's explicit `approveReviewEvent` action creates the transaction atomically; dismissing an event never changes the ledger.
