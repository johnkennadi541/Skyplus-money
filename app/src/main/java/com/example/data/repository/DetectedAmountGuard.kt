package com.example.data.repository

/**
 * Rejects invalid amounts before notification-derived transactions can reach the ledger.
 *
 * Notification detection writes through its own Room transaction and bypasses
 * FinancialTransactionRepository, so this invariant is enforced at this boundary too.
 */
internal fun requirePositiveDetectedAmount(amountMinor: Long) {
  require(amountMinor > 0L) { "Detected transaction amount must be greater than zero" }
}
