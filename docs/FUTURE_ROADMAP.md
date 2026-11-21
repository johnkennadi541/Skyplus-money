# SKYPLUS MONEY — Approved Future Requirements (record only)

These requirements were approved by the founder for **future phases**. They are
recorded here so they are not lost, and they are **not implemented**. Nothing in
this file is built, wired or enabled in the current codebase.

Each item must be implemented only in its own approved phase, after explicit
founder approval.

---

## A. Global Currency Selector

- All countries' currencies, using ISO 4217 codes.
- Show currency code, name, country and symbol.
- Searchable currency selector.
- Preserve the original transaction currency — never rewrite history.
- Never combine unlike currencies without an explicitly supported conversion.

Status: **not implemented**.

---

## B. Contextual Smart Calculator

- No separate "SKYPLUS Smart Calculator" title and no separate calculator button.
- The calculator appears automatically when the user enters an amount field in the
  relevant Add/Edit workflow.
- Supports brackets, percentage, addition, subtraction, multiplication, division
  and decimals.
- Supports expression editing, backspace and clear.
- Uses a safe expression parser — never `eval()`.
- Financial precision stays on integer minor units / `BigDecimal`.

Status: **not implemented**.

---

## C. Category-Based Entry UI

Separate category sets for income, expenses, loans and investments.

Expenses must include: Food, Movies & Entertainment, Bills & Utilities, Travel,
Shopping, Send to Others, Bank Transfer, Health, Transport, Education and Other.
**Other is mandatory.**

Recognisable icons: Food — fork and spoon; Movies — clapperboard; Travel —
suitcase; Bills — receipt; Bank Transfer — bank; Other — a general-purpose icon.

Custom category creation, icon selection, edit and delete are required.

The same contextual calculator experience should eventually reach EMI, loan,
budget, savings and other amount-entry areas.

Status: **not implemented**.

---

## Related open decision

Google Sign-In currently fails on CI-built debug APKs because the CI job creates a
throw-away debug keystore on every run, so the signing SHA-1 never matches the
fingerprint registered in Firebase. A single stable debug keystore, stored as a CI
secret, is required before sign-in can work on a CI-built APK. Not part of the UI
redesign.
