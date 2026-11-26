package com.example.core.network

/**
 * Network connectivity contract for offline-first financial operations.
 * Full ConnectivityManager monitoring and Firebase Firestore/Drive adapters are implemented in Phases 1 & 5.
 */
enum class ConnectivityStatus {
  ONLINE,
  OFFLINE,
  UNKNOWN,
}

interface ConnectivityObserver {
  fun currentStatus(): ConnectivityStatus
}
