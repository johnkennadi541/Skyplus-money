# SKYPLUS MONEY — Known Issues & Pending Items

1. **Google Play SMS Policy Restriction on Direct SMS Parsing**
   - Personal finance apps do not qualify as Default SMS Handlers under Google Play's SMS & Call Log Permissions Policy (`READ_SMS` / `RECEIVE_SMS`). Direct SMS inbox reading is intentionally disabled (`SmsPolicyStatusScreen`); automatic detection operates via explicit user-consented `NotificationListenerService`.
2. **Physical Device / Streaming Emulator Verification Pending**
   - This cloud build environment has no Android Emulator, physical device, or ADB. Live Google Sign-In UI, SQLCipher native `.so` runtime on physical ARM hardware, and Android system `NotificationListenerService` callbacks from third-party banking/UPI apps require verification on an Android device or Streaming Emulator.
3. **Facebook Login Excluded**
   - Google Sign-In is active; Facebook Login remains unimplemented per Phase 2 scope instructions.
4. **Legal Review of Draft Terms & Privacy Documents Required Before Production**
   - Phase 1D Legal Consent Gate (`v1.0.0`) texts remain marked as DRAFT placeholders pending formal legal counsel review prior to commercial release.
5. **Release Signing SHA-1 / SHA-256 Fingerprints**
   - Only Debug signing fingerprints are registered in `skyplus-money`. Release keystore fingerprints must be registered before Play Store release builds.
6. **Scheduled Phase 3–6 Features Remaining**
   - Budgets & Debts/EMIs (Phase 3), Investments, Shopping Lists & Reports/Charts (Phase 4), Cloud Sync & Encrypted Drive Backup (Phase 5), and Family Co-Admin Finance (Phase 6) remain roadmap items.
