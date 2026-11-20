# Stable debug signing keystore — setup (GitHub App, Android-only)

One stable debug keystore keeps the signing certificate identical on every build, so
the SHA-1 and SHA-256 registered in Firebase stay valid and Google Sign-In works.

Provisioning happens once, inside GitHub Actions, through a short-lived GitHub App
token. No computer, no terminal, no online generator, no third party ever holds the
key.

**This document contains no secret values.** The App private key and the four
signing secrets live only in the protected `debug-signing` environment.

---

## Order of operations

Do these five things in this order. Each step is a browser action on the phone.

1. Create the protected environment.
2. Create the GitHub App.
3. Add two environment secrets for the App.
4. Run the provisioning workflow once.
5. Register the printed fingerprints in Firebase.

---

## 1. Create the protected environment

GitHub → repository → **Settings → Environments → New environment** → name it
exactly **`debug-signing`**.

Configure:

- **Required reviewers**: add yourself.
- **Prevent self-review**: **OFF**. With it on, you are the only reviewer and the run
  can never be approved.
- **Deployment branches**: *Selected branches* → add the rule **`main`**. No other
  branch may use this environment.
- Wait timer: optional.

The provisioning workflow refuses to run if this environment has **no** protection
rules, so do not skip this step.

## 2. Create the GitHub App

GitHub → **Settings → Developer settings → GitHub Apps → New GitHub App**.

- Name: anything, for example `skyplus-debug-signing`.
- Homepage URL: the repository URL is fine.
- **Webhook: untick Active.** The App needs no webhook and no callback URL.
- Repository permissions, and nothing else:

| Permission | Level | Why |
|---|---|---|
| **Secrets** | Read and write | writes the four signing secrets |
| **Environments** | Read and write | approved for this design; lets the App read the environment it targets |

Every other permission stays at *No access*. Metadata read is granted automatically
and cannot be removed.

Then **Install App** → choose this account → **Only select repositories** →
`Skyplus-money`.

> Note on permissions, stated plainly: the environment-secrets API is authorised by
> the **Secrets** permission. `Environments` alone does not write secret values.
> Both are requested because both were approved; if you prefer the absolute minimum,
> grant `Secrets: Read and write` only and remove the `permission-environments` line
> from the provisioning workflow.

## 3. Add the App credentials to the environment

In the App page, note the **App ID**, then **Generate a private key**. GitHub
downloads a `.pem` file once.

GitHub → repository → **Settings → Environments → `debug-signing` → Add secret**:

| Secret name | Value |
|---|---|
| `SKYPLUS_APP_ID` | the App ID number |
| `SKYPLUS_APP_PRIVATE_KEY` | the entire contents of the `.pem` file, including the BEGIN and END lines |

To paste the key on a phone: open the downloaded `.pem` in a private text editor, select
all, copy, paste into the secret field, then clear the clipboard and delete the file.
Use a private notes app, never a chat or an email.

## 4. Run the provisioning workflow once

Actions → **Provision debug signing keystore** → **Run workflow** → branch **`main`**.

Because the job uses the protected environment, it waits for your approval: open the
run, review it, and **Approve**. Then it:

1. mints a one-hour GitHub App installation token, scoped to this repository and to
   those two permissions;
2. refuses to continue if the environment is missing or unprotected;
3. generates the keystore with **randomly generated passwords** (nobody ever types or
   sees them) under `$RUNNER_TEMP` with `0600` permissions;
4. prints **only** the public SHA-1 and SHA-256;
5. writes the four secrets into `debug-signing`
   (`SKYPLUS_DEBUG_KEYSTORE_BASE64`, `SKYPLUS_DEBUG_STORE_PASSWORD`,
   `SKYPLUS_DEBUG_KEY_PASSWORD`, `SKYPLUS_DEBUG_KEY_ALIAS`), piping values on stdin
   so they never appear in a command line;
6. verifies all four secret **names** exist;
7. deletes every temporary file, even if an earlier step failed;
8. never uploads an artifact.

## 5. Register the fingerprints in Firebase

Firebase Console → project `skyplus-money` → Project settings → Your apps → Android
app `com.skyplus.money` → **Add fingerprint** → paste the SHA-1, then add the
SHA-256. Allow five to ten minutes for propagation.

## 6. Afterwards

- The App private key can stay in the environment: it is only usable for `Secrets`
  and `Environments` on this one repository.
- To rotate, **Generate a private key** again in the App settings, replace
  `SKYPLUS_APP_PRIVATE_KEY`, and delete the old key in GitHub.
- To rotate the **keystore** itself, re-run the provisioning workflow. A new keystore
  means a new fingerprint, which must be registered in Firebase again.

---

## What CI does now

`.github/workflows/android-ci.yml` has two jobs:

| Job | Environment | When | What |
|---|---|---|---|
| `build-and-test` | none | every push and pull request | compile + full test suite. No secrets, no approval needed |
| `debug-apk` | `debug-signing` | **`main` only** | validates the four secrets, restores the keystore, builds, verifies the APK certificate equals the keystore, uploads the artifact |

**Consequence to be aware of:** a pull request no longer produces a debug APK
artifact, because the signing secrets are reachable only from the protected
environment on `main`. APKs come from `main` pushes. If you want PR artifacts back,
the `debug-apk` job can be extended to run on pull requests with the environment
declared — every such run would then need your approval.

## Failure behaviour

- Missing secret → the job fails with a clear error; no replacement keystore is ever
  generated.
- Keystore cannot be opened or the alias is wrong → fail before building.
- APK certificate differs from the keystore → fail; nothing is uploaded.
- Provisioning workflow without a protected environment → fail before generating
  anything.
- Temporary material is always removed, on success and on failure.

## Backup and recovery

There is **no backup copy** of the keystore: it exists only as an encrypted secret.
Recovery means rotation — re-run provisioning, re-register the new fingerprint in
Firebase, and reinstall debug APKs. This is acceptable because it is a **debug** key:
it cannot publish to Play and cannot sign a release build.

## Release signing

Untouched. The `release` config still reads `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD`. No release key exists or was changed.
