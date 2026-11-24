# SKYPLUS MONEY — welcome email deployment checklist

The Android app invokes the callable function only after a signed-in user's UID-scoped profile name has been initialized. The callable function:

- derives the recipient exclusively from the verified Firebase Authentication token;
- accepts only the profile first name, never a client-supplied email address;
- sends one outbound welcome email through Resend;
- records an idempotency marker in the Admin SDK-only `welcomeEmailDeliveries/{uid}` collection;
- does not request Gmail scopes, read inboxes, or process email notifications;
- does not log the recipient address, API key, or email body.

## Required project setup (not performed by this code change)

1. Confirm the Firebase project used by `app/google-services.json` is the intended production project.
2. Enable Firebase Cloud Functions and Firestore for that project. Cloud Functions deployment requires a billing-enabled Firebase plan; review the project's billing limits before enabling it.
3. Verify a sender domain/address with Resend.
4. Set the function secret:
   ```sh
   firebase functions:secrets:set RESEND_API_KEY
   ```
5. Configure the `WELCOME_FROM_EMAIL` parameter with the verified sender address when prompted during deployment.
6. Deploy only after reviewing the target project and costs:
   ```sh
   firebase deploy --only functions:sendWelcomeEmail
   ```

No function has been deployed and no billing, sender-domain, or API-key configuration has been changed by this implementation.
