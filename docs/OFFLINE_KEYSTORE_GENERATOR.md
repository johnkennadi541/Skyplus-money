# SKYPLUS Offline Key Vault — prototype

This is a separate Android application module. It intentionally has no INTERNET permission and does not modify the finance app's signing configuration.

## Current prototype scope

- Generates an RSA-4096 key pair on-device.
- Creates a self-signed X.509 certificate for the upload alias.
- Writes a JKS keystore into the app's private no-backup directory.
- Reopens the JKS and verifies the alias/key entry before reporting success.
- Displays the certificate SHA-256 fingerprint.
- Does not log or transmit the entered password; it clears its temporary character array after the operation.

## Important limitations — not production-ready

- No encrypted backup/export or restore flow is implemented yet.
- No password confirmation / strength estimator / secure backup workflow yet.
- The private JKS currently remains in app-private storage and is not exportable through the UI.
- Android provider compatibility for RSA-4096 and JKS must be proven on the target Samsung device and CI before production use.
- The displayed certificate fingerprint must be independently checked against a restored JKS and the eventual APK signature.
- The prototype must not be used for production signing.

## Build

`./gradlew :keystore-generator:assembleDebug`

The module is deliberately isolated from `:app`. Do not merge or distribute a production signing key until encrypted backup, restore verification, device testing, and independent security review pass.
