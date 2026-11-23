# SKYPLUS MONEY — Complete Experience & Feature Specification

Status: Approved requirements; implementation in progress
Target branch: `feature/complete-app-experience`
Base: `main`
APK/build distribution: Explicitly deferred until implementation, tests, security review, and CI are complete.

## Product-wide non-negotiables
- Single-user personal-finance app. No family/shared accounts, co-admin, or collaboration.
- Mandatory Google sign-in, UID-scoped data, legal-consent gate.
- Preserve SQLCipher + Android Keystore fail-closed behavior. No plaintext fallback, destructive migrations, key regeneration, or sensitive financial/credential logging.
- India-only investment instruments and Indian bank balance enquiry. Global ISO 4217 currency support across the finance app.
- Exact monetary arithmetic: Long minor units / BigDecimal; never Float/Double for money. Never silently combine currencies.
- User approval before creating financial entries from detected messages.
- No APK build, release, deployment, signing-key changes, or merge to main without separate explicit approval.

## 1. Credit Card Management — approved: ALL options
Keep existing card/account linkage and liability treatment. Extend with:
- Statement cycle/statement generation date and statement balance.
- Total amount due, minimum amount due, due date, payment status/history.
- Payment reminders and upcoming due calendar.
- Interest, late fee, annual fee, joining fee tracking.
- Rewards points and cashback ledger.
- Card-wise monthly spend and merchant/category breakdown.
- Multiple-card comparison, utilisation warnings, over-limit warning.
- Active/inactive/frozen status (freeze is a local tracking state only; no bank control is implied).
- Preserve existing issuer, network, last-four, limit, APR, cycle and due-offset fields.
- Never store full PAN, CVV, PIN, OTP, or online banking credentials.
- Payments must reconcile through the existing financial ledger; prevent duplicate posting and double counting. Statement/payment schema must be UID-scoped and migration-tested.

## 2. Investments — India only; purchase-entry-first
Supported categories: Indian listed stocks/equities, Indian ETFs, Indian mutual funds, and gold instruments available in India (including Gold ETFs where a verified quote feed exists). Exclude foreign-listed securities and global brokerage instruments.
- User enters a BUY transaction once: instrument identity, quantity, purchase date, executed purchase price, charges and broker/platform (optional).
- Do not ask the user to manually maintain current market price during purchase. Purchase price is the cost basis, not the live quote.
- After BUY, fetch latest market price automatically when a supported, lawful/reliable provider exists. Revalue holding and calculate appreciation/depreciation, absolute/percentage P&L, allocation, daily price change and price history.
- Store quote source, quote timestamp/as-of date, currency and freshness. Never overwrite purchase price/cost basis with a market quote.
- Unsupported instruments/provider outage/rate limit: retain last known quote, visibly mark stale/unavailable, never fabricate or silently set price to zero.
- Provider support is per asset class. AMFI/MFapi EOD NAV is only mutual-fund NAV; it is not a stock/ETF/gold quote source. Add stock/ETF/gold feeds only after verifying coverage, terms, rate limits, reliability and whether free use is permitted. No paid API dependency is assumed.
- Selling/disposal, if offered, is a separate SELL transaction and must preserve realised P&L/cost basis; do not require recurring manual price entries.
- Add animated portfolio allocation donut, value/performance transitions, gain/loss indicators and historical chart. Respect reduced-motion setting and never animate misleading price changes.

## 3. Global currency support — approved: ALL ISO currencies
- Use Java/Android ISO 4217 currency registry and per-currency fraction digits; reject pseudo/no-currency codes and invalid values.
- Support all runtime-available real ISO currencies, not a hard-coded short list.
- Preserve original transaction/account/investment currency. Conversion is a presentation/reporting layer only unless an explicit conversion transaction is recorded.
- FX rates need provider/source/as-of metadata, stale-rate handling and explicit base currency. Never present an unavailable rate as current or convert silently.
- Investment instruments remain India-only even when app currency display is global.

## 4. Indian bank balance enquiry — Quick Action
- Add a Bank Balance Enquiry quick action with official bank-specific registered-mobile missed-call/SMS instructions and confirmation before opening dialer/SMS composer.
- No credentials, OTP, PIN, CVV or passwords are requested, read, stored or forwarded.
- Parse only relevant bank balance/transaction notification content available through an explicitly enabled supported Android notification-access flow; show extracted details for review before posting.
- Google Play restricts READ_SMS/RECEIVE_SMS to eligible default SMS handlers. This personal-finance app must not bypass that policy, request restricted SMS permissions, scrape inboxes, or use Accessibility/hidden APIs. If bank SMS is not exposed through a permitted notification, explain the limitation and provide a safe manual refresh/entry route.
- Ignore and discard authentication/OTP/security messages. Never persist raw notification bodies beyond the minimum transient parsing need. Store only user-approved financial transaction data.
- User approval is required before any ledger entry. Deduplicate and validate account/currency/amount/date.

## 5. UI / UX redesign — approved direction
- App-wide professional, clean, modern visual language inspired by the clarity of Google and Microsoft Fluent, without copying their branding.
- Replace gold accent with a consistent yellow accent; maintain navy/white foundations and accessible contrast.
- Distinct, meaningful icons for every feature/category (income, expense, investments, gold, stocks, ETFs, mutual funds, loans, cards, reports, accounts).
- Cohesive launch, sign-in, welcome/onboarding, dashboard, detail, forms, settings and empty/error/loading states.
- Smooth purposeful transitions, animated allocation charts, price/performance movement and micro-interactions. Respect reduced motion and accessibility.
- Light/dark themes, responsive layouts, typography/spacing consistency, accessible labels and touch targets. Avoid decorative motion that obscures financial data.

## 6. Delivery gates
1. Requirements/specification and architecture audit.
2. Implement credit-card schema/domain/repository/use cases/UI and migration tests.
3. Implement global currency selection/catalog and FX abstraction/tests.
4. Implement India-only market instrument identity and per-category quote providers; provider contract tests and stale-price behavior.
5. Implement compliant notification-only bank detection and review queue/quick action; policy and security tests.
6. Complete app-wide UI redesign and animations with screenshot/Robolectric tests.
7. Run full unit, integration, migration, static/security and CI checks. Fix every failure.
8. Final read-only review; report incomplete/provider-limited features honestly.
9. Only after separate approval, build/sign APK and provide a verified install link.

## Explicit non-goals
- No unrestricted SMS inbox reading or OTP/password collection.
- No global stock/investment trading coverage.
- No paid market-data API assumption.
- No autonomous financial posting without user approval.
- No APK build during implementation.
