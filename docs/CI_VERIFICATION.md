# CI verification runs

This file records why an empty, documentation-only commit sometimes appears on a
verification branch. It exists so that a pull request can trigger the normal CI
workflow without touching application code.

## Rules followed by a verification branch

- Only documentation or a comment changes. No Kotlin, Java, layout, theme, Gradle,
  database, migration, authentication, Firebase, signing, secret or test file.
- The base is always `feature/ui-redesign`, never `main`.
- The pull request stays Draft and is never merged by the automation that opened it.
- A verification branch is deleted only by the founder, never automatically.

## What a verification run proves

`compileDebugKotlin` and `testDebugUnitTest` on the exact merged code, with the real
test counts printed by the *Summarise test results* step. It does not prove anything
about the signed APK: APK signing depends on the protected `debug-signing`
environment and on the signing configuration, which live on `main`.
