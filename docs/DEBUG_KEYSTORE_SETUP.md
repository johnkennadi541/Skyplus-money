# Stable debug signing keystore — setup (GitHub App, Android-only)

One stable debug keystore keeps the signing certificate identical on every build, so
the SHA-1 and SHA-256 registered in Firebase stay valid and Google Sign-In works.

Provisioning happens once, inside GitHub Actions, through a short-lived GitHub App
token. No computer, no terminal, no online generator, no third party ever holds the
key.

**This document contains no secret values.**

---

## ⚠ Read this before generating any key

**A newly generated keystore cannot update an APK that is already installed under a
different signing certificate.** Android refuses such an install. See the section
"APK update compatibility" below for the evidence and the only two possible outcomes.

---

## 1. Create the protected environment (manual, required first)

GitHub → repository → **Settings → Environments → New environment** → name it
exactly **`debug-signing`**.

- **Required reviewers**: add yourself.
- **Prevent self-review**: **OFF**, otherwise a solo founder can never approve.
- **Deployment branches**: *Selected branches* → add exactly **`main`**. One branch
  only, no wildcard.

The provisioning workflow verifies all of this at runtime and fails closed if any part
is missing, so this step is not optional.

## 2. Create the GitHub App (manual)

GitHub → **Settings → Developer settings → GitHub Apps → New GitHub App**.

- Name: for example `skyplus-debug-signing`. Homepage: the repository URL.
- **Webhook: untick Active.** No webhook, no callback URL.
- Repository permissions:

| Permission | Level | Why this permission exists |
|---|---|---|
| **Environments** | Read and write | read the environment and its deployment branch policy, and reach the environment secret endpoints |
| Metadata | Read | granted automatically to every App; cannot be removed |

Nothing else. No contents, no actions, no administration, no workflows.

Then **Install App** → **Only select repositories** → `Skyplus-money`.

### About the Secrets permission — read this carefully

The workflow currently requests **only** `Environments: write`, as approved.

GitHub's REST documentation for *Create or update an environment secret* states that
a GitHub App must have the **`secrets`** repository permission to call that endpoint;
`environments` alone does not write secret values. Because that conflicts with the
approved permission set, the workflow does not guess: it performs an explicit
**preflight** against the environment secret public-key endpoint before generating
anything, and fails closed with a precise message if the token is not authorised.

If the preflight fails, the fix is exactly two lines:

1. App permissions: add **Secrets — Read and write**.
2. In `.github/workflows/provision-debug-keystore.yml`, add
   `permission-secrets: write` next to `permission-environments: write`.

That change needs the founder's approval, because it widens the token.

## 3. Add the App credentials to the environment (manual)

In the App page, note the **App ID**, then **Generate a private key** (one-time `.pem`
download).

GitHub → **Settings → Environments → `debug-signing` → Add secret**:

| Secret name | Value |
|---|---|
| `SKYPLUS_APP_ID` | the App ID number |
| `SKYPLUS_APP_PRIVATE_KEY` | the whole `.pem` file, including the BEGIN and END lines |

On a phone: open the `.pem` in a private editor, select all, copy, paste, then clear
the clipboard and delete the file. Never send it through chat or email.

## 4. Run the provisioning workflow once (manual)

Actions → **Provision debug signing keystore** → **Run workflow** → branch **`main`**.
Approve the deployment when prompted.

What it does, in order:

1. mints a one-hour GitHub App installation token, scoped to this repository with
   `environments: write` only, from an action pinned to a commit SHA;
2. refuses to run outside `main`;
3. verifies the environment **already exists**, has **required reviewers configured**,
   and has a **custom deployment branch policy listing exactly `main`** — the number
   of protection rules is not treated as proof;
4. proves the token can reach the environment secret API, and fails closed with the
   exact remedy if it cannot;
5. generates the keystore with randomly generated passwords that nobody sees, under
   `$RUNNER_TEMP` with `0600` permissions;
6. prints only the public SHA-1 and SHA-256;
7. writes the four secrets with values piped on stdin (`--body` only for the
   non-secret alias);
8. verifies the four secret **names**;
9. deletes every temporary file in an `if: always()` step; never uploads an artifact.

## 5. Register the fingerprints in Firebase (manual)

Firebase Console → project `skyplus-money` → Project settings → Your apps → Android
app `com.skyplus.money` → **Add fingerprint** → SHA-1, then SHA-256. Allow five to ten
minutes.

---

## APK update compatibility — evidence and blockers

**Question:** can the next APK update the APK already installed on the founder's phone
without uninstalling it?

**Answer: no, not with a newly generated keystore. This is unresolved and blocks
distribution.**

Evidence gathered read-only:

| Item | Finding |
|---|---|
| Application ID | `com.skyplus.money`, unchanged |
| Installed APK certificate (diagnostics build, run 44745920493) | SHA-1 `94:68:14:4F:69:F4:BC:F7:0C:F4:89:27:17:14:BA:FE:11:B1:DB:08`, SHA-256 `12:92:31:D3:E5:E3:E2:14:02:4D:82:5E:F4:5B:24:DC:1A:64:FF:87:DC:AD:C9:68:F7:8E:9A:A3:95:E4:A9:E5` — re-extracted from the APK artifact and also shown on-device by the DEBUG diagnostics card |
| A later CI build (run 44766111158) | SHA-1 `23:B7:F5:50:5E:96:C4:2C:DD:62:9E:8F:A5:BF:D0:11:1D:BD:77:54` — a different certificate |
| Private key behind either certificate | **not recoverable**: each was generated inside a throw-away CI runner and never stored anywhere |
| Repository history | contains no `.jks`, `.keystore` or `debug.keystore`; all are git-ignored (`.gitignore` lines 18, 21, 22) |
| Firebase-registered hash in `google-services.json` | `c10d778e…706c3`, matching neither build |

**Conclusion.** The installed certificate is *known*, but the private key behind it is
*lost*. Android requires an identical signing certificate for an update, so a new
keystore produces an APK that can only be installed fresh. Per the audit instruction,
no replacement key may be generated for the purpose of updating the existing install.

**How to obtain the installed APK's fingerprints safely**, without a computer:

1. Install the diagnostics build whose DEBUG card prints its own signing SHA-1 and
   SHA-256 (that is how `94:68:14…` was confirmed on the device), or
2. compute them from any APK file with `apksigner verify --print-certs` or
   `keytool -printcert -jarfile`, or
3. read the same values from the CI job log where the APK was built.

None of these needs root, ADB or a third-party app.

**The two possible outcomes, both requiring founder approval:**

- **A. Recover the original key.** Only possible if a copy exists outside the CI
  runner. The evidence says it does not. If you believe you still have an APK signed
  with a key you own, tell me and I will verify it before anything else.
- **B. Accept one reinstall.** The founder uninstalls the current debug build and
  installs the new stable-key APK once. Local app data is lost, which for this build
  is test data only: sign-in never succeeded, so no financial records were ever
  created. After that single reinstall, every future build updates normally because
  the certificate never changes again.

## Versioning

Current state in `app/build.gradle.kts`: `applicationId = "com.skyplus.money"`,
`versionCode = 1`, `versionName = "1.0"`.

Rules that must hold for every distributed APK:

- application ID never changes;
- `versionCode` strictly increases for each distributed build;
- signing certificate identical to the previously installed build;
- no destructive database migration and no app-data reset.

Proposed approach (needs approval, not yet applied): set `versionCode = 2` for the
first stable-key build, then increment by one for every distributed build, and let
`versionName` carry the human-readable number (`1.0.1`, `1.1`, …). One line changes in
`app/build.gradle.kts`.

## Test APK for pull requests

Today `debug-apk` runs only on `main`, so pull requests produce no installable APK.

**Rejected as unsafe:** attaching the `debug-signing` environment to pull-request jobs.
The environment's branch policy is `main` only, so using it on a PR would mean
relaxing that policy, and every PR run would then need an approval — while a fork PR
must never reach signing secrets at all.

**Proposed (not implemented):** add a separate `preview-apk` job that runs on pull
requests, builds with the standard throw-away debug keystore and uploads
`skyplus-money-preview-apk`. It uses **no secrets at all**, so nothing can leak. Its
artifact is clearly labelled as a preview that cannot update the stable install and on
which Google Sign-In will fail, because its certificate is random.

That gives review builds without weakening secret protection. It needs approval
before implementation.

## Failure behaviour

- Missing secret → clear error; no replacement keystore is generated.
- Environment missing, unprotected, without reviewers, or not restricted to `main` →
  fail before generating anything.
- Token cannot reach the secret API → fail before generating anything, with the exact
  remedy printed.
- APK certificate differs from the keystore → fail; nothing is uploaded.
- Temporary material always removed, on success and on failure.

## Release signing

Untouched. The `release` config still reads `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD`. No release key exists or was changed.
