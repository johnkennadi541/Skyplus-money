# SKYPLUS MONEY — Security Documentation

## 1. Authentication & Session Security
- Firebase Auth is the authoritative session source through `FirebaseAuthDataSource` and `AuthStateListener`.
- The app requires an authenticated session; guest access is disabled by `DefaultSecurityPolicy`.
- Google Credential Manager ID tokens are exchanged with Firebase Auth and are not persisted or logged.
- Automatic account linking based only on matching email addresses is prohibited.
- Startup session refresh signs out invalid or disabled cached users.
- The authenticated workspace is additionally protected by the in-memory biometric/device-credential app lock. `FLAG_SECURE` is enabled by `MainActivity`.

## 2. Consent & Data Governance
- Current Terms and Privacy consent is required before entering the financial workspace.
- Consent records are keyed by Firebase UID; background transaction detection fails closed when consent is missing, outdated, or unreadable.
- Financial DAO reads, updates, and deletes are scoped by authenticated `userId`; update methods also reject entity ownership mismatches.
- Inserts use `OnConflictStrategy.ABORT` rather than replacing records on primary-key collisions.

## 3. Local Financial Database Encryption
- Room `AppDatabase` is currently schema version **11** and is configured through `DatabaseModule`.
- SQLCipher is required on release builds and real Android devices. Plaintext fallback is permitted only for local debug Robolectric tests when native SQLCipher libraries are unavailable.
- The database passphrase is a random 256-bit value wrapped by an AES-256-GCM master key in Android Keystore.
- Existing database files without recoverable key metadata are not assigned a replacement key; initialization fails closed.
- Android platform backup is disabled, with explicit backup/device-transfer exclusions retained in the XML rules.
- Room migrations are explicit through version 11; destructive migration fallback is not configured.

## 4. Smart Transaction Detection Privacy
- The notification listener reads notification extras only after confirming that the notification belongs to the device's current default SMS app.
- It requires Android Notification Access, current legal consent, an authenticated Firebase UID, and the user's detection opt-in.
- Notification title/body lengths are bounded before parsing. The parser rejects sensitive OTP/PIN/CVV/passcode patterns.
- Raw notification/SMS message bodies are not persisted; only structured transaction metadata and sanitized sender/title data are stored.
- No SMS-reading/receiving permission or Accessibility Service is declared in the Android manifest.
- The listener does **not** currently process a general allowlist of banking/UPI app notifications; its source boundary is the current default SMS app. This documentation reflects the implementation.

## 5. Backup / Restore Status — Not Complete
- AES-256-GCM account-bound backup-envelope primitives, recovery-key wrapping primitives, a narrow Google Drive `drive.file` authorization client, and a bounded Drive REST API exist in source.
- The documented end-to-end recovery flow is not complete: database snapshot packaging, portable database-key handling, restore orchestration, explicit user-facing backup/restore controls, scheduling, and end-to-end integration tests remain outstanding.
- Do not describe cloud backup or restore as active or complete. No Drive permission has been requested and no backup file has been uploaded by this code review.

## 6. Server Function Boundary
- The welcome-email callable requires an authenticated Google provider session and a verified email claim; recipient address is taken from the auth token, not caller input.
- The Resend API key is configured as a server secret and is not shipped in the Android app.
- The repository does not contain Firestore/Storage security-rules files, so deployed Firebase rules cannot be verified from this repository snapshot.

## 7. Audit Scope & Verification
- This document describes source-level controls reviewed in the repository; it is not a third-party penetration test or a verification of live Firebase project configuration.
- CI compilation and automated tests provide regression evidence but do not replace real-device SQLCipher/Keystore testing, dependency vulnerability scanning, or independent security testing.
