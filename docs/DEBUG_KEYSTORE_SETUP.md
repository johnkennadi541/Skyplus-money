# Stable debug signing keystore — setup (GitHub App, Android-only)

One stable debug keystore keeps the signing certificate identical on every build, so
the SHA-1 and SHA-256 registered in Firebase stay valid and Google Sign-In works.

Provisioning happens once, inside GitHub Actions, through a short-lived GitHub App
token. No computer, no terminal, no online generator, no third party ever holds the
key.

**This document contains no secret values.**

---

## Current state — the keystore is already provisioned

**Provisioning is finished. Do not run it again.** The stable debug keystore exists, and
all four signing secrets are already stored in the protected `debug-signing` environment:

- `SKYPLUS_DEBUG_KEYSTORE_BASE64`
- `SKYPLUS_DEBUG_STORE_PASSWORD`
- `SKYPLUS_DEBUG_KEY_ALIAS`
- `SKYPLUS_DEBUG_KEY_PASSWORD`

The repository variable `DEBUG_CERT_SHA256` also already exists and holds the approved
fingerprint recorded in "Required repository configuration" below.

`SKYPLUS_APP_ID` and `SKYPLUS_APP_PRIVATE_KEY` are **separate GitHub App credentials**,
used only to mint the short-lived token that the provisioning workflow needs. They are not
signing material and they are not part of the four signing secrets.

> **NEVER rerun the provisioning workflow.** It could rotate the signing identity, and a
> new identity breaks every future APK update: an APK signed with a different certificate
> cannot install over an existing one. The workflow carries a one-time guard that refuses
> to run when any of the four secrets already exists; that guard is the protection, not
> an invitation to try again.

The numbered sections below are kept as a record of how the keystore was provisioned and
what each value means. They are **reference, not a to-do list**. Nothing in them needs to
be repeated.

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

The provisioning workflow verifies the reviewer and branch-policy configuration at
runtime and fails closed if any part is missing, so this step is not optional.

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

### About the permission the workflow uses

The workflow requests **only** `Environments: write`, the least-privilege permission
for creating and updating environment secrets. No other write permission is granted.

Before generating anything, the workflow performs a **preflight** that verifies the
token can read the environment secret public key. That call proves public-key **read**
access on the environment secrets endpoint; it does not assert anything about write
permission. If it fails, the job stops with a message asking you to check that the App
has `Environments: Read and write`, that the installation covers this repository, and
that the token requests `permission-environments: write`.

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

## 3b. Getting the workflow onto `main` (needs approval)

`workflow_dispatch` only works for a workflow file that exists on the default branch, so
this workflow cannot run from `main` until it is there. Nothing has been merged.

Two safe routes, both needing the founder's approval:

- **Route A — minimal PR into `main`.** A new branch containing only
  `.github/workflows/provision-debug-keystore.yml`, the two-job
  `.github/workflows/android-ci.yml` and this document, opened directly against `main`.
  It does not carry the UI redesign or any app code. Between the merge and the first
  provisioning run, pushes to `main` will show a failing `debug-apk` job with the
  message that the keystore is not provisioned yet — that is the designed fail-closed
  behaviour, not a defect.
- **Route B — merge PR #4 first.** Merge `fix/stable-google-signin-keystore` into
  `feature/ui-redesign`, then `feature/ui-redesign` into `main`. Slower and it carries
  the whole redesign, so Route A is recommended.

Never commit directly to `main`, and never dispatch the workflow from a branch other
than `main`.

## 4. How provisioning was done (already completed — do not repeat)

> This step is finished. It is documented so the mechanism can be audited, not so it can
> be run again. Rerunning it risks rotating the signing identity and breaking every future
> APK update.

Actions → **Provision debug signing keystore** → **Run workflow** → branch **`main`**.
Approve the deployment when prompted.

What it does, in order:

1. mints a one-hour GitHub App installation token, scoped to this repository with
   `environments: write` only, from an action pinned to a commit SHA;
2. refuses to run outside `main`;
3. verifies the **configuration** that must be in place: required reviewers actually
   configured, and a **custom deployment branch policy listing exactly `main`** with no
   wildcard and no second branch. The number of protection rules is not treated as
   proof. Referencing an environment in a workflow can cause GitHub to create it, so
   this check does not claim the environment existed beforehand — it fails closed when
   the required configuration is absent;
4. verifies the token can read the environment secret public key, and fails closed if
   it cannot;
5. generates the keystore with randomly generated passwords that nobody sees, under
   `$RUNNER_TEMP` with `0600` permissions;
6. prints only the public SHA-1 and SHA-256;
7. writes the four secrets with values piped on stdin (`--body` only for the
   non-secret alias);
8. verifies the four secret **names**;
9. deletes every temporary file in an `if: always()` step; never uploads an artifact.

## 5. Firebase fingerprints — already registered

**Owner-confirmed.** The repository owner confirms that both the SHA-1 and the SHA-256 of
the stable debug keystore have been added to the Android app `com.skyplus.money` in the
Firebase project `skyplus-money`. The approved SHA-256 registered there is the one
recorded in "Required repository configuration" below:
`47:B6:93:4D:D5:B4:2A:D1:79:E4:2C:70:4A:8D:97:7E:4F:35:1E:7C:54:F4:C1:26:E4:4F:D5:F3:4B:42:E2:EB`.

This is the owner's confirmation of a console action. It is **not** an independent live
verification: no workflow, tool or repository file can read the Firebase Console, so
nothing here claims one was performed.

For the record, the console path used was: Firebase Console → project `skyplus-money` →
Project settings → Your apps → Android app `com.skyplus.money` → **Add fingerprint** →
SHA-1, then SHA-256. Fingerprint changes take five to ten minutes to propagate.

**Where these values live.** The signing fingerprints are project configuration held in
the Firebase Console. They are *not* stored in `google-services.json`: that file carries
the project id, API key and client ids, and an Android OAuth client entry without a
`certificate_hash`, so it cannot tell you which signing certificate Firebase accepts.
Reading the repository therefore proves nothing about Firebase. The console is the only
source of truth, and the fingerprints there must be compared by hand against the approved
values recorded in this document.

---

## APK update compatibility — evidence and blockers

**Question:** can the next APK update the APK already installed on the founder's phone
without uninstalling it?

**Answer:** not for the build installed today. That build was signed with a throw-away
key whose private key is lost, so no APK produced now can install over it in place. This
applies **only** to that one installed build: every build produced from the stable
keystore shares one certificate, so all builds from now on update each other normally.

Evidence gathered read-only:

| Item | Finding |
|---|---|
| Application ID | `com.skyplus.money`, unchanged |
| Installed APK certificate (diagnostics build, run 44745920493) | SHA-1 `94:68:14:4F:69:F4:BC:F7:0C:F4:89:27:17:14:BA:FE:11:B1:DB:08`, SHA-256 `12:92:31:D3:E5:E3:E2:14:02:4D:82:5E:F4:5B:24:DC:1A:64:FF:87:DC:AD:C9:68:F7:8E:9A:A3:95:E4:A9:E5` — re-extracted from the APK artifact and also shown on-device by the DEBUG diagnostics card |
| A later CI build (run 44766111158) | SHA-1 `23:B7:F5:50:5E:96:C4:2C:DD:62:9E:8F:A5:BF:D0:11:1D:BD:77:54` — a different certificate |
| Private key behind either certificate | **not recoverable**: each was generated inside a throw-away CI runner and never stored anywhere |
| Repository history | contains no `.jks`, `.keystore` or `debug.keystore`; all are git-ignored (`.gitignore` lines 18, 21, 22) |
| Signing fingerprints inside `google-services.json` | **none**. The file carries a web client and an Android client with no `certificate_hash` field, so it says nothing about which signing certificate is accepted |
| Firebase Console registration | **owner-confirmed**. The repository owner confirms both the SHA-1 and the SHA-256 were added to the Android app in the Firebase Console. This is the owner's statement, not an independent live check: the fingerprints are project configuration held in the Firebase Console, not in `google-services.json`, so they cannot be read from this repository |

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
- **B. The installed build is replaced, once.** Only the owner decides this, and only
  after checking what is actually stored in that install. This document does **not**
  state that the installed app can be removed safely and does **not** state that its
  local data is disposable — that is the owner's decision, not an assumption to make on
  their behalf. After any such replacement, every later build updates normally, because
  the certificate never changes again.

## Versioning

Current state in `app/build.gradle.kts` **on this PR branch
(`fix/stable-google-signin-keystore`)**: `applicationId = "com.skyplus.money"`,
`versionCode = 2`, `versionName = "1.0"`.

The `versionCode` on `main` is still 1; this branch raises it to 2 for the first build
signed with the stable keystore. Merge that decision deliberately, not by accident.

Rules that must hold for every distributed APK:

- application ID never changes;
- `versionCode` strictly increases for each distributed build;
- signing certificate identical to the previously installed build;
- no destructive database migration and no app-data reset.

Applied on this branch: `versionCode = 2` for the first stable-key build. From here,
increment by one for every distributed build, and let `versionName` carry the
human-readable number (`1.0.1`, `1.1`, …).

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

## Post-provisioning verification checklist

Run these in order after the provisioning workflow succeeds and before anyone tests
Google Sign-In.

1. **Fingerprints exist.** The provisioning run printed one `SHA1:` and one `SHA256:`
   line. Record them; they are public.
2. **APK signature verified by CI.** The `debug-apk` job compares the APK certificate
   with the keystore certificate and fails if they differ. Do not distribute an APK
   from a run where that step did not pass.
3. **APK fingerprint matches the keystore.** Read `keystore SHA-1` and `APK SHA-1`
   from the verify step log; they must be identical, and the same for SHA-256.
4. **Firebase registration — done.** Both fingerprints were added to the Android app
   `com.skyplus.money` in project `skyplus-money` (owner-confirmed). Nothing further is
   needed here; this item is recorded as completed, not as a task.
5. **Version code.** The first stable APK must carry `versionCode = 2` or higher —
   it is 2 in this branch — because the installed debug build is version 1.
6. **Approved fingerprint pinned.** The `debug-apk` job also compares the APK
   certificate against the repository variable `DEBUG_CERT_SHA256`. A missing variable,
   an unreadable certificate or a different fingerprint fails the job before upload. See
   "Required repository configuration" below.
7. **Install and test.** Install the verified APK, then try Google Sign-In. Sign-in
   must not be tested on any APK whose certificate is not the registered one.

## Failure behaviour

- Missing secret → clear error; no replacement keystore is generated.
- Environment missing, unprotected, without reviewers, or not restricted to `main` →
  fail before generating anything.
- Token cannot reach the secret API → fail before generating anything, with the exact
  remedy printed.
- APK certificate differs from the keystore → fail; nothing is uploaded.
- APK certificate differs from `DEBUG_CERT_SHA256` → fail; nothing is uploaded.
- `DEBUG_CERT_SHA256` is unset or is not 64 hex characters → fail; nothing is uploaded.
- The provisioning guard finds **any one** of the four `SKYPLUS_DEBUG_*` environment
  secrets already present → the provisioning run stops before any key is generated, and
  no secret is overwritten. A failed secret listing stops the run as well (fail closed).
- Temporary material always removed, on success and on failure.

## Release signing

Untouched. The `release` config still reads `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD`. No release key exists or was changed.

## Required repository configuration

Both items below are **already configured** by the repository owner. Neither is set by
any workflow and neither is stored in this repository. They are recorded here so they can
be checked, not so they can be created again.

### 1. Repository variable `DEBUG_CERT_SHA256`

The `debug-apk` job refuses to upload an APK unless its signing certificate SHA-256
equals this variable.

- Settings → Secrets and variables → Actions → **Variables** → New repository variable.
- Name: `DEBUG_CERT_SHA256`
- Value: the approved stable debug certificate SHA-256, 64 hex characters. Colons,
  spaces and lower case are accepted and normalised before comparison. The approved
  value is
  `47:B6:93:4D:D5:B4:2A:D1:79:E4:2C:70:4A:8D:97:7E:4F:35:1E:7C:54:F4:C1:26:E4:4F:D5:F3:4B:42:E2:EB`.
  This value is fixed by the owner. It must not be replaced by any other fingerprint.
- It is a public value; it is never a secret. Do not derive it from a `keytool` run on a
  different keystore — a fingerprint taken from the wrong keystore will silently lock the
  `debug-apk` job into failing.

If this variable is ever missing, or holds anything other than the approved fingerprint,
the `debug-apk` job fails on purpose and uploads nothing. That is the intended behaviour,
not a broken build: an unverifiable APK must not ship.

### 2. The provisioning guard is one-time

`provision-debug-keystore.yml` lists the environment secrets before generating anything
and stops if **any one** of the four `SKYPLUS_DEBUG_*` secrets already exists — which it
does, so every future dispatch stops at the guard. Replacing the signing identity is
therefore not possible by simply running the workflow again.

If the identity ever has to change, that is a deliberate, reviewed procedure, not a
re-run:

1. Accept that every installed APK becomes non-updatable and needs one uninstall.
2. Record the current fingerprints and the reason for the change in this document.
3. Update `DEBUG_CERT_SHA256` and the Firebase Console fingerprints together, in the
   same maintenance window.
4. Only then remove the four `SKYPLUS_DEBUG_*` environment secrets by hand and run the
   provisioning workflow once more.
