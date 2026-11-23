package com.example.core.security

/**
 * In-memory app-lock state. Authentication never implies biometric unlock;
 * every foreground session must pass the platform prompt before workspace UI is composed.
 */
data class AppLockState(
  val isAuthenticated: Boolean = false,
  val isUnlocked: Boolean = false,
) {
  val requiresUnlock: Boolean
    get() = isAuthenticated && !isUnlocked

  fun onAuthenticationChanged(authenticated: Boolean): AppLockState =
    if (authenticated) AppLockState(isAuthenticated = true, isUnlocked = false)
    else AppLockState()

  fun onBackground(): AppLockState =
    if (isAuthenticated) copy(isUnlocked = false) else this

  fun onUnlockSucceeded(): AppLockState =
    if (isAuthenticated) copy(isUnlocked = true) else this
}
