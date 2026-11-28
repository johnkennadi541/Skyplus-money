# SKYPLUS Offline Key Vault — prototype

This is a separate Android application module. It intentionally has no INTERNET permission and does not modify the finance app's signing configuration.

## Current prototype scope

- Generates an RSA-4096 key pair on-device and stores it under the JKS `upload` alias.
- Reopens the JKS, checks the RSA key/certificate modulus, and signs/verifies a random challenge before reporting success.
- Uses Android's Storage Access Framework to export/import a user-selected backup file; no broad storage permission is requested.
- Encrypts backup bytes with AES-256-GCM (128-bit authentication tag), using PBKDF2-HMAC-SHA256 with a random 16-byte salt and 600,000 iterations.
- Authenticates the backup header as GCM additional data and rejects unknown format/KDF parameters, modified ciphertext, wrong passwords, and oversized files.
- Requires matching confirmation fields when creating the keystore and backup passwords.
- On restore, decrypts to a temporary file, validates the JKS, alias, RSA-4096 key/certificate match, and private-key proof, then replaces the local keystore with rollback staging.
- Clears password character arrays and temporary byte arrays where practical; passwords and private keys are never logged or transmitted.
- Includes unit tests for encryption/decryption, wrong password, tampering, unsupported headers, short passwords, and invalid payload sizes.

## Backup format

The `.skybackup` file is a custom binary envelope, **not a 7z archive**. Version 1 layout:

1. 8-byte magic/version marker (`SKYJKS01`)
2. 4-byte big-endian PBKDF2 iteration count (fixed at 600,000 for version 1)
3. 16-byte random salt
4. 12-byte random AES-GCM nonce
5. Ciphertext followed by the 16-byte GCM authentication tag

The backup password is not stored in the file. Keep it separately from the backup. Losing it makes the backup unrecoverable. A forgotten original JKS password also prevents JKS validation during restore.

## Important limitations — not production-ready

- Android device/runtime compatibility for RSA-4096, the JKS provider, PBKDF2-HMAC-SHA256, and AES-GCM must be tested on the target Samsung device. CI compilation and unit tests do not prove device compatibility.
- The certificate subject fields are prototype metadata only; they are not proof of legal company identity.
- The displayed SHA-256 certificate fingerprint must be recorded and independently compared after restore and against the eventual APK signature.
- The current implementation is a security-sensitive prototype and has not received an independent security review. Do not use this key for production signing yet.
- App uninstall or clearing app data removes the local keystore; preserve and test the encrypted backup first.

## Build and test

`./gradlew :keystore-generator:assembleDebug`

`./gradlew :keystore-generator:testDebugUnitTest`

The module is deliberately isolated from `:app`. Do not merge or distribute a production signing key until CI, target-device testing, backup/restore verification, and independent security review pass.
