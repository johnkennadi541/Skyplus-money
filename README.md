# SKYPLUS MONEY

**Tagline:** *Take Control. Build Wealth.*  
**Platform:** Android (Kotlin + Jetpack Compose)  
**Package ID:** `com.skyplus.money`  
**Architecture:** Clean Architecture + MVVM + Hilt DI  
**Current Status:** **Phase 0 — Foundation**

---

## Overview

SKYPLUS MONEY is an offline-first, security-hardened personal and family wealth management Android application built with a **Deep Ocean Blue + Teal + Gold** visual identity.

### Core Principles
- **Zero-Floating-Point Monetary Math:** All monetary values use integer minor units (`Long`) paired with `BigDecimal` and explicit ISO 4217 currency precision (`Money`). `Double`/`Float` are strictly prohibited for monetary calculations.
- **Mandatory Authentication (Phase 1+):** No Guest Mode and no unauthenticated access to financial screens. Separate Google Sign-In and Facebook Login providers via Firebase Auth with verified ownership before account linking.
- **Offline-First & Encrypted Storage (Phase 2+):** SQLCipher-encrypted Room database protected via Android Keystore, with `android:allowBackup="false"` enforced in the manifest.
- **Zero Ads:** Core financial capabilities remain ad-free.
- **Zero Mock Financial Data:** Foundation screens clearly communicate upcoming phases without fabricating transactions, balances, or fake login flows.

## Phase Roadmap
- **Phase 0 — Foundation (Current):** Clean project structure, `com.skyplus.money` package ID, Ocean Design System, Hilt DI, Navigation Compose foundation, `Money` engine, automated unit & Robolectric tests, documentation, and CI workflow.
- **Phase 1 — Authentication:** Firebase Auth, Google Sign-In, Facebook Login, Terms & Privacy consent tracking, account linking, logout, and account deletion.
- **Phase 2 — Core Finance:** Accounts/wallets, income/expenses, atomic transfers, multi-currency, encrypted Room + SQLCipher.
- **Phase 3 — Financial Management:** Budget planner, savings goals, loans, EMI schedules, credit cards, financial health score.
- **Phase 4 — Advanced Finance:** Investment portfolio, net worth, shopping lists, analytics charts, PDF/DOCX/CSV reports.
- **Phase 5 — Cloud & Backup:** Optional Firestore cloud sync, conflict resolution, optional encrypted Google Drive backup.
- **Phase 6 — Family Finance:** Family groups, equal Co-Admin permissions, shared ledgers, actor-excluding FCM notifications.
- **Phase 7 — Final Release:** Full regression, security/migration audits, signature verification, and release preparation.

## Running Verification Locally

```bash
gradle :app:compileDebugKotlin
gradle :app:testDebugUnitTest
```
