# SKYPLUS MONEY — Security Documentation

## 1. Authentication & Session Security (Phase 1B — Preserved)
- **Authoritative Session Source:** `FirebaseAuth` (`Firebase.auth`) via `AuthStateListener` inside `FirebaseAuthDataSource`.
- **Mandatory Gate:** `SkyplusAuthGateRoot` blocks all 6 financial navigation tabs and all Phase 2 financial screens unless `AuthSessionState` is `Authenticated`. `DefaultSecurityPolicy.allowsGuestMode` is `false`.
- **No Prohibited Fallbacks:** `signInAnonymously()` and `signInWithEmailAndPassword()` are never used anywhere in `app/src/main/`.
- **Account Linking Policy:** Email-only automatic account merging is prohibited (`FirebaseAuthUserCollisionException` maps to `AuthError.AccountLinkingRequiresOwnershipVerification`).
- **Session Validation:** `RefreshAuthSessionUseCase` reloads the cached Firebase user on startup; invalid or disabled users are signed out cleanly.

## 2. Consent & Data Governance (Phase 1D — Preserved)
- **Mandatory Consent Gate:** Authenticated users cannot enter `SkyplusAppShell` until current Terms (`1.0.0`) and Privacy (`1.0.0`) versions are explicitly accepted.
- **UID Isolation:** Consent records, financial accounts, financial transactions, detection events, and account mapping rules are strictly keyed and queried by Firebase `userId` (`uid`).

## 3. Local Financial Database Encryption (Phase 2)
- **Room + SQLCipher:** `AppDatabase` is currently schema version 11 and is configured in `DatabaseModule` with `net.sqlcipher.database.SupportFactory` and `SQLiteDatabase.loadLibs(context)`. Production opens use the explicit non-destructive migration chain from version 1 through version 11 (`MIGRATION_1_2` through `MIGRATION_10_11`).
- **Android Keystore Passphrase:** `DatabasePassphraseProvider` generates a cryptographically random 256-bit (32-byte) database passphrase wrapped with an AES-256-GCM master key stored in `AndroidKeyStore`.
- **Backup Restriction:** `android:allowBackup="false"` is enforced in `AndroidManifest.xml`. `backup_rules.xml` and Android 12+ `data_extraction_rules.xml` additionally exclude the encrypted database and its WAL/SHM/journal sidecars, the Keystore-wrapped passphrase vault preferences, and notification-detection settings from cloud backup and device transfer.
- **Non-Destructive Migrations:** All registered Room migrations from 1→11 are explicit; destructive fallbacks (`fallbackToDestructiveMigration`) are prohibited. The complete 8→11 migration path is covered by integration validation.

## 4. Smart Transaction Detection Privacy & Google Play Policy Controls (Phase 2)
- **Zero OTP / PIN / CVV Persistence:** `SmartTransactionParser` rejects any alert matching `\b(OTP|One Time Password|secret code|verification code|security code|do not share|CVV|CVC|PIN|MPIN|TPIN|UPI PIN|ATM PIN|password|passcode)\b` before extraction.
- **No Raw Message Storage:** `TransactionDetectionEventEntity` contains NO raw SMS or raw notification body fields; only structured metadata (`detectedAmountMinor`, `detectedCurrency`, `detectedType`, `detectedMerchant`, `detectedAccountLast4`, `referenceId`, `dedupFingerprint`) and masked titles (`sanitizeSenderTitle` replaces 6+ consecutive digits with `••••`).
- **Zero Sensitive Logging:** Notification contents, account numbers, and transaction payloads are never written to Logcat.
- **App Allowlist & Explicit Opt-In:** `SkyplusNotificationListenerService` (protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`) runs only after explicit user Android Notification Access grant, respects user pause toggle (`isNotificationDetectionEnabled`), and processes only notifications whose package name is in the user's permitted financial app set.
- **Google Play SMS Policy Compliance:** `READ_SMS`, `RECEIVE_SMS`, and `BIND_ACCESSIBILITY_SERVICE` are NOT declared anywhere in `AndroidManifest.xml` or source code.

## 5. Static Security Check
Verified zero matches in `app/src/main/` for prohibited auth fallbacks, hardcoded OAuth client IDs, `READ_SMS`/`RECEIVE_SMS`/`BIND_ACCESSIBILITY_SERVICE` declarations, or financial payload logging.
