# SKYPLUS MONEY — Requirements Baseline

> **Documentation Note:** This file tracks verified project requirements across completed phases (Phase 0, Phase 1, and Phase 2).

## 1. Identity & Platform
- **App Name:** SKYPLUS MONEY
- **Tagline:** Take Control. Build Wealth.
- **Package ID:** `com.skyplus.money`
- **Platform:** Android (Kotlin, Jetpack Compose, Material Design 3, Clean Architecture + MVVM + Hilt)
- **Language:** English | **Market:** Global
- **Design System:** Deep Ocean Blue + Teal + Gold (Light & Dark themes)

## 2. Authentication & Consent Requirements (Phase 1 — Preserved)
- **Mandatory Authentication:** Immediate authentication required on launch; **no Guest Mode** and no unauthenticated access to financial tabs.
- **Google Sign-In:** Jetpack Credential Manager + Google ID (`GetSignInWithGoogleOption` using generated `R.string.default_web_client_id`) exchanged with Firebase Authentication (`GoogleAuthProvider.getCredential()`).
- **Facebook Login:** Explicitly excluded / deferred per project instruction.
- **Account Linking:** Never auto-link accounts solely by matching email address; verified ownership is required.
- **Phase 1D Legal Consent Gate:** Mandatory Terms & Conditions and Privacy Policy consent gate (`v1.0.0`) with UID-isolated persistence via Jetpack DataStore before accessing the dashboard. Draft legal documents require formal legal review prior to commercial release.

## 3. Core Finance Management (Phase 2A–2D — Implemented)
- **Account Management (2A):** Create, edit, safe-delete (blocked when linked transactions exist unless explicitly confirmed), activate/deactivate multiple Bank, Cash, Savings, Wallet, and Credit Card accounts with ISO 4217 multi-currency support (`KWD` 3 decimals, `INR`, `USD`, `EUR`, `AED`, `SAR`, `GBP`, custom ISO codes) and account-specific ledgers.
- **Monetary Precision:** Never use `Double` or `Float` for monetary calculations; use integer minor units (`Long`) and `BigDecimal` (`RoundingMode.HALF_EVEN`) with ISO 4217 scale metadata. Never mix currencies without explicit conversion.
- **Income & Expense Management (2B/2C):** Dedicated Income and Expense management flows with categories, merchant/payee, notes, date/time, search, and time-period / category / account / currency filters.
- **Centralized Transaction Engine (2D):** Supports `INCOME`, `EXPENSE`, `TRANSFER`, `REFUND`, `REVERSAL`, `PENDING`, `FAILED`; atomic internal transfers (`TRANSFER_OUT` + `TRANSFER_IN` legs) with cross-currency converted amount validation and double-counting prevention.

## 4. Smart Transaction Detection & Automatic Save (Phase 2E–2H — Implemented)
- **Notification Detection:** `NotificationListenerService` (`SkyplusNotificationListenerService`) with explicit Android Notification Access setup, user toggle, permitted financial apps allowlist, and per-account mapping rules (`AccountMappingRule`).
- **Google Play SMS Policy Compliance:** `READ_SMS` and `RECEIVE_SMS` are restricted by Google Play's SMS & Call Log Permissions Policy to the user's Default SMS Handler. Personal finance apps are ineligible; SMS reading permissions are NOT requested, SMS detection remains disabled with a dedicated status explanation screen (`SmsPolicyStatusScreen`), and no Accessibility Service or hidden API workarounds are used.
- **On-Device Parser & Security:** Offline rule-based `SmartTransactionParser` with zero-tolerance rejection of OTP/PIN/MPIN/TPIN/CVV/password alerts, multi-currency extraction, documented confidence scoring (`HIGH`/`MEDIUM`/`LOW`), 3-tier `DeduplicationEngine` (SHA-256 fingerprint + reference ID + 10-minute window), high-confidence auto-save with in-app notice banner, Review Queue with Transaction Correction UI, and encrypted local persistence via Room v2 + SQLCipher + Android Keystore (`MIGRATION_1_2` non-destructive).
- **Budget & Cost:** ₹0 — zero paid APIs or external cloud services required.
