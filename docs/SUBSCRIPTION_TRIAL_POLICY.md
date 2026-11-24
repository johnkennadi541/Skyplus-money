# SKYPLUS MONEY — Subscription and Trial Policy

Status: approved product requirement; implementation must remain in Draft PR #21 until the full security and CI gates pass.

## Customer-facing policy

- Annual price: INR 499 per year.
- Trial welcome: show a one-time in-app welcome message on the user's first app activation. Do not send a push or promotional email during the first 30 days.
- Trial period: 30 full days of unrestricted access.
- Reminder period: on trial day 30 through day 44, send at most one respectful subscription push notification per local calendar day (15 reminders total). The user retains full access throughout this reminder period.
- Email: send one outbound subscription reminder email when the reminder period begins (day 30). Send only to the verified email address from the authenticated Google/Firebase account. Never request Gmail scopes, read inboxes, or process email notifications.
- Final notice: on day 45, send one final notice explaining that access is now locked unless the user subscribes.
- Lock: from day 45, gate all paid app workspace/features behind an active, verified annual subscription. Keep the subscription screen, account/sign-out controls, support/help and legal/privacy information accessible.
- Reactivation: a later successful subscription restores access to the existing user account and all previously stored data.
- Data retention: trial expiry or subscription expiry must never delete, clear, overwrite, or reset user financial data, account records, transactions, reports, profile, or database encryption keys. No automatic account deletion.

## Identity, timing and reliability requirements

- The trial must be one-time per authenticated Firebase UID, not reset by reinstall, app-data recreation, device change, or local clock manipulation.
- Record the first activation time using a trusted server timestamp. Store trial/entitlement state server-side and scope every record to the authenticated UID.
- Client-side dates, local preferences, and client-supplied entitlement flags are not authoritative.
- Schedule reminders idempotently; reschedule safely after reboot, time-zone changes, app updates, sign-in changes, and WorkManager retries. Never send duplicate reminders for the same UID and trial day.
- Respect Android notification permission. If push is unavailable, do not repeatedly prompt or bypass permission.
- A notification tap should open the subscription screen.
- Do not expose financial data in notification or email content.

## Purchase and entitlement security

- Use Google Play Billing for the INR 499 annual subscription.
- Unlock only after the purchase token has been verified by a trusted backend against Google Play Developer API and the verified entitlement is stored server-side for the authenticated UID.
- Handle pending, cancelled, expired, refunded, revoked, and restored purchases explicitly. A pending purchase must not unlock the app.
- Never trust a client-only purchase-success callback or a locally stored boolean as proof of payment.
- The Play Console subscription product/base plan, backend service-account permissions/secrets, sender-domain configuration, and Firebase deployment are external setup gates. Do not invent credentials, deploy functions, enable paid services, or incur costs without explicit approval.

## Implementation boundaries

- Preserve the existing SMS-only transaction-capture boundary. Subscription email is outbound-only; no Gmail/Email inbox access or email notification processing.
- Preserve SQLCipher fail-closed behavior, UID isolation, existing Room data, and all existing financial records.
- Do not build an APK, release, deploy, or merge PR #21 as part of this work.
- PR #21 must remain Open and Draft until implementation, security review, and full CI are complete.
