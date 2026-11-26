package com.example.core.sync

/**
 * Offline-first synchronization contract.
 * Phase 5 implements WorkManager background sync, Firestore conflict resolution,
 * and optional per-user encrypted Google Drive backups.
 */
enum class SyncState {
  NOT_CONFIGURED,
  IDLE,
  SYNCING,
  PENDING_LOCAL_CHANGES,
  CONFLICT_DETECTED,
}

data class SyncSettingsSpec(
  val cloudSyncEnabled: Boolean = false,
  val googleDriveBackupEnabled: Boolean = false,
)
