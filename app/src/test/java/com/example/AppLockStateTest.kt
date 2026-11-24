package com.example

import com.example.core.security.AppLockState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockStateTest {

  @Test
  fun `authenticated session is locked until platform authentication succeeds`() {
    val state = AppLockState().onAuthenticationChanged(authenticated = true)
    assertTrue(state.requiresUnlock)
    assertFalse(state.isUnlocked)

    val unlocked = state.onUnlockSucceeded()
    assertTrue(unlocked.isUnlocked)
    assertFalse(unlocked.requiresUnlock)
  }

  @Test
  fun `backgrounding relocks the complete authenticated workspace`() {
    val unlocked = AppLockState()
      .onAuthenticationChanged(authenticated = true)
      .onUnlockSucceeded()

    val backgrounded = unlocked.onBackground()
    assertTrue(backgrounded.requiresUnlock)
  }

  @Test
  fun `sign out clears authenticated and unlocked state`() {
    val unlocked = AppLockState()
      .onAuthenticationChanged(authenticated = true)
      .onUnlockSucceeded()

    val signedOut = unlocked.onAuthenticationChanged(authenticated = false)
    assertFalse(signedOut.isAuthenticated)
    assertFalse(signedOut.isUnlocked)
    assertFalse(signedOut.requiresUnlock)
  }

  @Test
  fun `unlock callback cannot unlock after sign out`() {
    val signedOut = AppLockState().onUnlockSucceeded()
    assertFalse(signedOut.isUnlocked)
    assertFalse(signedOut.requiresUnlock)
  }
}
