# Changelog

## [0.4.0] - Phase 2: Core Finance Management & Smart Transaction Detection
### Added
- **Phase 2A Account Management:** Added `FinancialAccount` / `AccountType` (`BANK`, `CASH`, `SAVINGS`, `WALLET`, `CREDIT_CARD`), `FinancialAccountDao`, `FinancialAccountRepositoryImpl`, `AccountsViewModel`, `AccountsScreen`, and `AccountDetailScreen` with ISO 4217 multi-currency support (`KWD`, `INR`, `USD`, `EUR`, `AED`, `SAR`, `GBP`, custom codes), active/inactive status toggle, safe delete warning when linked transactions exist, and account-specific ledgers.
- **Phase 2B/2C/2D Income, Expense & Centralized Transaction Engine:** Added `FinancialTransaction`, `TransactionType` (`INCOME`, `EXPENSE`, `TRANSFER`, `REFUND`, `REVERSAL`, `PENDING`, `FAILED`), `TransactionStatus`, `FinancialTransactionDao`, `FinancialTransactionRepositoryImpl`, `TransactionsViewModel`, `TransactionsScreen`, `IncomeManagementScreen`, and `ExpenseManagementScreen` with atomic balance calculations in `Long` minor units / `BigDecimal`, search, date-range/account/category/currency/type filters, and linked internal transfers (`TRANSFER_OUT_CATEGORY` / `TRANSFER_IN_CATEGORY`) with explicit cross-currency conversion validation.
- **Phase 2E/2F Smart Transaction Detection (read-only + explicit approval):** Implemented `SkyplusNotificationListenerService`, `NotificationPermissionHelper`, `SmartTransactionParser` (multi-currency rule engine + strict OTP/PIN/CVV/password rejection + confidence scoring), `DeduplicationEngine` (SHA-256 fingerprint + reference ID + 10-minute window inside atomic DB transactions), `SmartDetectionRepositoryImpl`, `SmartDetectionViewModel`, and dedicated Compose screens (`SmartDetectionSettingsScreen`, `NotificationAccessSetupScreen`, `SmsPolicyStatusScreen`, `ReviewQueueScreen`, `TransactionCorrectionDialog`, `AccountMappingScreen`, `DetectionHistoryScreen`).
- **Phase 2G Google Play SMS Policy Verification:** Documented why finance trackers are ineligible for `READ_SMS` / `RECEIVE_SMS` under Google Play's SMS & Call Log Permissions Policy; kept SMS permissions undeclared and SMS detection disabled with no Accessibility Service or hidden API workarounds.
- **Phase 2H Security, Database Encryption & Automated Testing:** Activated Room v2 (`AppDatabase`) with SQLCipher (`4.5.4`), `DatabasePassphraseProvider` (Android Keystore AES-256-GCM wrapped 256-bit passphrase), and non-destructive `DatabaseMigrations.MIGRATION_1_2`. Added `Phase2SmartDetectionParserTest`, `Phase2DatabaseAndRepoTest`, `Phase2SecurityAuditTest`, and `Phase2UiRobolectricTest` (total suite: 51/51 passing).

## [0.3.0] - Phase 1D: Legal Consent Gate
### Added
- Implemented `ConsentRecord` domain model with version tracking (`1.0.0`) and `ConsentRepository` interface.
- Implemented `ConsentPreferencesDataSource` and `ConsentRepositoryImpl` using Jetpack DataStore with UID-isolated consent records.
- Added `CheckUserConsentUseCase` and `SaveUserConsentUseCase`.
- Created `LegalConsentScreen` featuring draft Terms & Conditions and Privacy Policy (v1.0.0) with expandable full text, dual mandatory checkboxes, legal review warning badge, and sign-out option.
- Gated authenticated access in `SkyplusAuthGateRoot` so users must provide explicit consent before accessing the dashboard.
- Added `ConsentLogicUnitTest` (5 tests) and `LegalConsentGateRobolectricTest` (5 tests); total automated test suite now 29/29 passing.

## [0.2.0] - Phase 1B: Google Sign-In
### Added
- Enabled Firebase BOM (`35.1.0`), `firebase-auth`, `androidx.credentials` (`1.5.0`), `credentials-play-services-auth` (`1.5.0`), and `googleid` (`1.1.1`) in `app/build.gradle.kts`, plus `INTERNET` permission in `AndroidManifest.xml`.
- Implemented domain authentication models (`AuthModels.kt`), `AuthRepository`, and use cases (`ObserveAuthSessionUseCase`, `SignInWithGoogleIdTokenUseCase`, `RefreshAuthSessionUseCase`, `SignOutUseCase`).
- Implemented `CredentialManagerGoogleClient` using `GetSignInWithGoogleOption` and build-generated `R.string.default_web_client_id`.
- Implemented `FirebaseAuthDataSource` (`AuthStateListener`, `GoogleAuthProvider.getCredential`, session reload validation, safe error mapping) and `AuthRepositoryImpl` with Credential Manager sign-out cleanup.
- Wired Hilt DI bindings in `AuthModule`.
- Implemented `AuthViewModel` with synchronous double-tap protection and `GoogleAuthScreen` / `AuthLoadingScreen` using the Deep Ocean Blue, Teal, and Gold design system.
- Enforced mandatory authentication at the root via `SkyplusAuthGateRoot` in `MainActivity` and `SkyplusAppShell`, plus Sign Out actions in `MoreScreen` and `AuthFoundationScreen`.
- Added `AuthViewModelAndRepositoryTest` (6 tests) and `AuthGateRobolectricTest` (3 tests); total suite 19/19 passing.
- Added `REQUIREMENTS.md`, `SECURITY.md`, `API_AND_INTEGRATIONS.md`, `TEST_REPORT.md`, `KNOWN_ISSUES.md`, and updated `docs/ARCHITECTURE.md`.

## [0.1.0] - Phase 0: Foundation
### Added
- Clean Android project foundation configured with `applicationId = "com.skyplus.money"` and app title `SKYPLUS MONEY`.
- Clean Architecture + MVVM package structure (`core/*`, `data/*`, `domain/*`, `feature/*`, `navigation/`).
- Hilt dependency injection (`SkyplusMoneyApp`, `CommonModule`, `DataModule`, `@HiltViewModel`).
- Ocean Design System (`OceanColors`, `OceanTypography` with bundled `Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`, `OceanShapes`, `SkyplusOceanTheme` Light & Dark modes, and adaptive components).
- Custom adaptive launcher icon (`skyplus_money_icon` + density mipmap PNGs).
- Adaptive Navigation Compose foundation (`NavigationBar` on compact screens, `NavigationRail` on >=600dp screens) across `Home`, `Transactions`, `Budgets`, `Investments`, `Reports`, and `More` plus `Settings`, `AuthReadiness`, and `LegalPreview` sub-screens.
- DataStore-backed theme preference persistence.
- `Money` domain value object using `Long` minor units and `BigDecimal` with ISO 4217 currency precision.
- Unit tests (`MoneyTest`, `HomeViewModelTest`) and Robolectric UI/manifest tests (`ExampleRobolectricTest`, `SkyplusNavigationRobolectricTest`).
- `README.md`, `docs/ARCHITECTURE.md`, and `.github/workflows/android-ci.yml`.
