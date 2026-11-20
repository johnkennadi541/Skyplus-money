# Stable Debug Keystore — one-time setup

Google Sign-In checks the signing certificate of the installed APK. CI used to
generate a brand-new debug keystore on every run, so every debug APK had a
different SHA-1 and the fingerprint registered in Firebase could never match.

This document describes the one-time procedure. It contains **no secrets**. The
keystore, its passwords and its base64 form live only on the founder's machine and
in GitHub Actions secrets.

---

## 1. Create the keystore once, on your own machine

```bash
keytool -genkeypair \
  -v \
  -keystore skyplus-debug.jks \
  -storetype JKS \
  -alias skyplusdebug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "<STORE_PASSWORD>" \
  -keypass "<KEY_PASSWORD>" \
  -dname "CN=SKYPLUS Debug,O=SKYPLUS,C=IN"
```

Keep `skyplus-debug.jks` somewhere safe and backed up. If it is lost, the
fingerprint changes and Firebase must be updated again.

## 2. Read its public fingerprints

```bash
keytool -list -v -keystore skyplus-debug.jks -alias skyplusdebug
```

Copy the `SHA1:` and `SHA256:` values. These are public and safe to share; the CI
workflow prints them too.

## 3. Add four repository secrets

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret name | Value |
|---|---|
| `SKYPLUS_DEBUG_KEYSTORE_BASE64` | `base64 -w0 skyplus-debug.jks` output |
| `SKYPLUS_DEBUG_STORE_PASSWORD` | the store password |
| `SKYPLUS_DEBUG_KEY_PASSWORD` | the key password |
| `SKYPLUS_DEBUG_KEY_ALIAS` | `skyplusdebug` |

Never paste these into a file, a commit, a PR description or a chat message.

## 4. Register the fingerprints in Firebase

Firebase Console → project `skyplus-money` → Project settings → Your apps →
Android app `com.skyplus.money` → **Add fingerprint** → paste the SHA-1, then add
the SHA-256 as well. Allow five to ten minutes for propagation.

## 5. What CI does with the secrets

1. Fails immediately with a clear error if any of the four secrets is missing — it
   never generates a replacement keystore.
2. Decodes the keystore into `$RUNNER_TEMP` with `0600` permissions.
3. Verifies the alias exists, using `-storepass:env` so no password reaches a
   command line or a log.
4. Prints only the public SHA-1 and SHA-256.
5. Builds the debug APK with that keystore.
6. Verifies the APK certificate equals the keystore certificate and fails the job
   if they differ.
7. Deletes the keystore in an `if: always()` step, including after a failure.
8. Never uploads the keystore as an artifact.

## 6. Local and AI Studio builds

When `SKYPLUS_DEBUG_KEYSTORE_PATH` is not set, `app/build.gradle.kts` falls back to
the git-ignored `./debug.keystore` with the usual `android` password, so local and
AI Studio builds keep working exactly as before. Only CI uses the stable keystore.

## 7. Release signing

Untouched. The `release` signing config still reads `KEYSTORE_PATH`,
`STORE_PASSWORD` and `KEY_PASSWORD`, and no release key was created or changed.
