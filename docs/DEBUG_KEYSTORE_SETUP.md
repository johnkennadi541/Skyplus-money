# Stable Debug Keystore — one-time setup (Android / Termux only)

Google Sign-In checks the signing certificate of the installed APK. CI used to
generate a brand-new debug keystore on every run, so every debug APK had a
different SHA-1 and the fingerprint registered in Firebase could never match.

This is a one-time procedure and it needs **no computer** — everything runs on the
Android phone in Termux. This document contains **no secrets**. The keystore, its
passwords and its base64 form stay on the phone and in GitHub Actions secrets, and
must never be pasted into a chat, a PR, a commit or a document.

---

## 1. Install the tools on the phone (Termux)

Install **Termux from F-Droid**, not from the Play Store (that build is deprecated).
Also install the **Termux:API** app from F-Droid if you want the clipboard step.

```bash
pkg update && pkg upgrade -y
pkg install openjdk-17 coreutils -y
pkg install termux-api -y        # optional, only for the clipboard step
```

`keytool` comes with `openjdk-17`. Check it:

```bash
keytool -help | head -3
```

## 2. Create the keystore once

Set the two passwords as environment variables so they never appear on a command
line. Use at least 8 characters. Do not reuse any other password.

```bash
export SKYPLUS_STORE_PASS='<your store password>'
export SKYPLUS_KEY_PASS='<your key password>'
```

```bash
keytool -genkeypair -v \
  -keystore ~/skyplus-debug.jks \
  -storetype JKS \
  -alias skyplusdebug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass:env SKYPLUS_STORE_PASS \
  -keypass:env SKYPLUS_KEY_PASS \
  -dname "CN=SKYPLUS Debug,O=SKYPLUS,C=IN"
```

Restrict the file and keep it private:

```bash
chmod 600 ~/skyplus-debug.jks
```

`~/skyplus-debug.jks` is inside Termux's private app storage, which other apps
cannot read. Back it up somewhere safe — if it is lost, the fingerprint changes and
Firebase must be updated again.

## 3. Read the public fingerprints

```bash
keytool -list -v \
  -keystore ~/skyplus-debug.jks \
  -alias skyplusdebug \
  -storepass:env SKYPLUS_STORE_PASS
```

Copy the `SHA1:` and `SHA256:` values. These two are public and safe to share; the
CI workflow prints the same values.

## 4. Make the base64 form for the secret

```bash
base64 -w0 ~/skyplus-debug.jks > ~/skyplus-debug.jks.b64
```

To copy it to the phone clipboard (needs Termux:API):

```bash
termux-clipboard-set < ~/skyplus-debug.jks.b64
```

Now open the GitHub repository in the phone browser and paste it into the secret.
Never paste it anywhere else.

## 5. Add four repository secrets

GitHub → **Settings → Secrets and variables → Actions → New repository secret**

| Secret name | Value |
|---|---|
| `SKYPLUS_DEBUG_KEYSTORE_BASE64` | contents of `~/skyplus-debug.jks.b64` |
| `SKYPLUS_DEBUG_STORE_PASSWORD` | the store password from step 2 |
| `SKYPLUS_DEBUG_KEY_PASSWORD` | the key password from step 2 |
| `SKYPLUS_DEBUG_KEY_ALIAS` | `skyplusdebug` |

## 6. Clear the passwords from the shell

```bash
unset SKYPLUS_STORE_PASS SKYPLUS_KEY_PASS
rm -f ~/skyplus-debug.jks.b64
```

Keep `~/skyplus-debug.jks` — it is the only copy of the key.

## 7. Register the fingerprints in Firebase

Firebase Console → project `skyplus-money` → Project settings → Your apps →
Android app `com.skyplus.money` → **Add fingerprint** → paste the SHA-1, then add
the SHA-256 as well. Allow five to ten minutes for propagation.

## 8. What CI does with the secrets

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

## 9. Local and AI Studio builds

When `SKYPLUS_DEBUG_KEYSTORE_PATH` is not set, `app/build.gradle.kts` falls back to
the git-ignored `./debug.keystore` with the usual `android` password, so local and
AI Studio builds keep working exactly as before. Only CI uses the stable keystore.

## 10. Release signing

Untouched. The `release` signing config still reads `KEYSTORE_PATH`,
`STORE_PASSWORD` and `KEY_PASSWORD`, and no release key was created or changed.
