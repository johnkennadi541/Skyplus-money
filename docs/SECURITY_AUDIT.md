# SKYPLUS MONEY — Source-Level Security Audit

**Review date:** 2026-11-23  
**Reviewed ref:** `3abd4fb757e74248abf4ab359ec3f8d6112aa904`  
**Scope:** Android app source, Room/SQLCipher boundary, authentication and consent gates, notification ingestion, Google Drive backup primitives, Firebase welcome-email callable, and GitHub Actions signing workflows.

This is a source-level review, not a penetration test, live Firebase configuration review, or independent cryptographic assessment.

## Confirmed controls

- Mandatory Firebase authentication and versioned Terms/Privacy consent gate.
- Android Keystore-backed AES-GCM wrapping of the SQLCipher database passphrase.
- Fail-closed SQLCipher initialization on release builds and real Android devices; plaintext fallback is limited to local debug Robolectric tests.
- Database schema version 11 with explicit migrations and no destructive migration fallback.
- Room DAO reads, updates, and deletes are scoped by Firebase UID; update helpers reject mismatched entity ownership.
- `OnConflictStrategy.ABORT` is used for audited financial inserts.
- App lock uses biometric/device credential and `FLAG_SECURE`; Android platform backup is disabled.
- Notification ingestion is restricted to the current default SMS app, bounded before parsing, gated by authenticated UID/current consent/user opt-in, and does not persist raw message bodies.
- Google Drive authorization requests the narrow `drive.file` scope. Backup envelope primitives use AES-256-GCM and bind ciphertext to the Firebase UID.
- Welcome-email callable requires a verified Google-authenticated Firebase session, derives the recipient from the auth token, and keeps the Resend API key server-side.

## Fixes made during this review

1. **Signing workflow branch selection:** the manual release-APK and one-time release-keystore workflows did not previously restrict execution to trusted `main` branch code. Added job-level `github.ref == 'refs/heads/main'` guards so a manually selected feature branch cannot execute those jobs with signing secrets.
2. **Investment transaction chronology:** additional buys and sells could be entered earlier than the latest recorded trade, potentially making the weighted-average-cost sequence inaccurate. Added a UID/holding-scoped latest-transaction-date query, repository validation, and a regression test rejecting backdated buys and sells.

## Residual risks / work not included in this pass

- **Firebase App Check:** the welcome-email callable enforces Firebase authentication but does not currently enforce App Check. Add App Check only with a coordinated Android client rollout and verified Firebase project configuration.
- **Firebase rules:** Firestore/Storage rules files are not present in this repository snapshot. Deployed rules and project-level access settings could not be verified.
- **Branch protection:** the GitHub integration could not read repository branch-protection settings. Confirm that `main` requires reviewed changes; the workflow branch guards do not replace branch protection.
- **Backup/restore is not complete:** encryption and Drive API primitives exist, but portable database-key packaging, safe atomic restore, user-facing controls, scheduled backup, and end-to-end restore tests remain outstanding. Do not treat cloud backup as active.
- **Device verification:** SQLCipher and Android Keystore behavior still needs real-device testing, including key invalidation and recovery scenarios.
- **Dependency/security scanning:** no successful dependency vulnerability scan or independent penetration test was available in this review.

## Verification

- The investment chronology fix was included in Android CI run **#475**, which completed successfully: **506 tests, 0 failures, 0 errors, 0 skipped**; Room migration integration test: **1 passed**.
- Later documentation and workflow-guard commits are being revalidated by the latest CI runs. Do not treat those newer runs as passed until GitHub reports completion.