# SKYPLUS MONEY — Google Drive backup security design

## Access boundary

- Google sign-in continues to use Credential Manager and Firebase Auth.
- Drive authorization is a separate, explicit user-consent flow requested only when the user enables backup.
- The Android authorization client requests only `https://www.googleapis.com/auth/drive.file`, not full Drive access.
- Drive REST calls are restricted to the app-created `SKYPLUS MONEY Backup.spm` file.
- Access tokens are passed in memory to the REST client and are not written to preferences, Room, logs, or backup payloads.
- The default Google account selected by Credential Manager is used for subsequent Google authorization requests.

## Backup encryption

- Backup payload encryption uses AES-256-GCM with a fresh 96-bit IV and 128-bit authentication tag per backup.
- A random 128-bit salt and PBKDF2-HMAC-SHA256 (310,000 iterations) derive the encryption key from the recovery secret.
- The authenticated Firebase UID is included as GCM additional authenticated data. A backup cannot be decrypted under another UID.
- Wrong recovery secrets, wrong UIDs, altered ciphertext, truncated envelopes, and unsupported versions fail closed.
- The SQLCipher database remains encrypted at rest locally. Its Android Keystore-wrapped key must be safely represented in the portable encrypted backup payload for cross-device restore; it must never be uploaded outside the authenticated encrypted envelope.

## Current implementation status

Implemented:
- AES-GCM account-bound backup envelope and regression tests.
- Google Drive authorization client using the narrow `drive.file` scope.
- Drive REST upload/update and download API restricted to the app's named backup file.
- MockWebServer contract tests for upload and download.
- Android screenshot protection and app-wide biometric/device-credential gate are implemented separately.

Still required before backup can be called complete:
- Recovery-key creation, secure local wrapping, one-time display/copy and confirmation that the user saved it.
- Consistent SQLCipher WAL checkpoint and database snapshot packaging, including portable database-key material inside the encrypted envelope.
- Safe restore flow that verifies UID and authentication tag, asks before replacing local data, installs the restored database key without key regeneration, replaces the database file atomically, and restarts into a fresh database instance.
- Explicit Enable Backup control, manual Backup Now and Restore UI.
- WorkManager automatic schedule, retry/backoff, account-change cancellation, and visible last-success/error status.
- Integration tests for database snapshot/restore, key-preservation, interrupted upload, duplicate backup update, wrong-account restore, and rollback prevention.

No Drive permission has been requested from a user, no file has been uploaded, and no automatic backup is active yet.
