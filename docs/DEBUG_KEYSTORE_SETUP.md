# Stable Debug Keystore — options for an Android-only founder

**Status: no method is approved yet. Nothing here has been implemented, and no
keystore has been created.**

## Why this file exists

Google Sign-In checks the signing certificate of the installed APK. CI used to
generate a new debug keystore on every run, so every APK had a different SHA-1 and
no fingerprint registered in Firebase could ever match. The fix in PR #4 makes CI
restore **one** keystore from four repository secrets instead of generating one:

| Secret name | Value |
|---|---|
| `SKYPLUS_DEBUG_KEYSTORE_BASE64` | the keystore file, base64 encoded |
| `SKYPLUS_DEBUG_STORE_PASSWORD` | store password |
| `SKYPLUS_DEBUG_KEY_PASSWORD` | key password |
| `SKYPLUS_DEBUG_KEY_ALIAS` | the key alias |

Until those four secrets exist, the workflow stops at "Validate debug signing
secrets" with a clear error, exactly as designed.

## The hard fact

A JKS keystore is produced by `keytool`, which ships inside a Java JDK. An Android
phone does not include a JDK. **Every method therefore needs something that runs
`keytool`** — either a computer, a terminal app, a cloud service, or a third-party
app that bundles a JDK. There is no menu inside Android that creates a keystore.

## Methods I assessed

| # | Method | Safe? | Usable for you? |
|---|---|---|---|
| 1 | Android Studio / any PC with a JDK | Yes — official Android tooling | No, you have no computer |
| 2 | Termux on the phone | Yes | **Rejected** — you do not want a terminal |
| 3 | Online keystore generators (browser sites) | **No** — a third party would hold your signing key | Rejected by you, and rightly |
| 4 | Play Store apps that bundle a JDK and build APKs on the phone | **Unverified** — I could not confirm that any of them exports a keystore, or how it stores the key | Not recommended |
| 5 | Cloud CI signing services that generate the key for you | **No** — the third party holds the key | Rejected |
| 6 | One-time GitHub Actions generation, written straight into the secrets (option A below) | Yes, with a short-lived token | **Needs your approval** |
| 7 | A trusted person with a computer runs the commands once (option B below) | Yes, if you trust that person with a *debug* key | **Needs your approval** |

**Plainly: I could not confirm any method that is both safe and phone-only without
a terminal.** Options A and B are the only two I can defend. I have implemented
neither.

---

## Option A — one-time generation inside GitHub Actions (proposed, not built)

Idea: a workflow that runs once on a branch, generates the keystore inside GitHub's
own runner, and writes the four secrets directly through the GitHub API. The key
never leaves GitHub's infrastructure and never appears in a log, an artifact, a
commit or a chat.

What it needs from you, all in the phone browser, no terminal:

1. Create a fine-grained personal access token in the GitHub app or site, limited to
   this repository, with the single permission **Secrets: write**.
2. Store it once as a temporary secret.
3. Run the workflow, then delete the token and the temporary secret.

Trade-offs you should weigh:

- You will never hold a copy of the key. If the secrets are deleted, the key is
  gone — for a *debug* key that only means generating a new one and registering the
  new fingerprint again.
- A token with secret-write access is powerful; it must be deleted immediately
  after the run.
- It still "creates a keystore", which your current instruction forbids, so it
  cannot start without your explicit approval.

## Option B — a trusted person with a computer (proposed, not built)

One run of the four commands in the appendix below, on any computer with a JDK.
That person sends you only the base64 text; you paste it into the GitHub secret from
the phone browser.

Trade-offs:

- Someone else briefly handles a debug signing key. For a debug key that is
  acceptable if you trust them; it is not a release key.
- You get a real backup: they can also send you the `.jks` file to keep in your own
  private storage.

---

## Appendix — reference commands (for option B only)

Run on a computer with a JDK. Passwords are supplied through environment variables
so they never appear on a command line or in shell history.

```bash
export SKYPLUS_STORE_PASS='<store password>'
export SKYPLUS_KEY_PASS='<key password>'

keytool -genkeypair -v \
  -keystore skyplus-debug.jks \
  -storetype JKS \
  -alias skyplusdebug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass:env SKYPLUS_STORE_PASS \
  -keypass:env SKYPLUS_KEY_PASS \
  -dname "CN=SKYPLUS Debug,O=SKYPLUS,C=IN"

chmod 600 skyplus-debug.jks

keytool -list -v \
  -keystore skyplus-debug.jks \
  -alias skyplusdebug \
  -storepass:env SKYPLUS_STORE_PASS

base64 -w0 skyplus-debug.jks > skyplus-debug.jks.b64

unset SKYPLUS_STORE_PASS SKYPLUS_KEY_PASS
```

The `SHA1:` and `SHA256:` lines from `keytool -list -v` are public and are the two
values you register in Firebase.

## Registering in Firebase (phone browser, no terminal)

Firebase Console → project `skyplus-money` → Project settings → Your apps →
Android app `com.skyplus.money` → **Add fingerprint** → paste the SHA-1, then add
the SHA-256. Allow five to ten minutes for propagation.

## What CI does once the secrets exist

1. Fails immediately with a clear error if any secret is missing — it never
   generates a replacement.
2. Decodes the keystore into `$RUNNER_TEMP` with `0600` permissions.
3. Validates the alias with `-storepass:env`.
4. Prints only the public SHA-1 and SHA-256.
5. Builds the debug APK with that keystore.
6. Verifies the APK certificate equals the keystore certificate, and fails if not.
7. Deletes the keystore in an `if: always()` step and never uploads it.

## Release signing

Untouched. The `release` config still reads `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD`. No release key exists or was changed.
