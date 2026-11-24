import { getAuth } from "firebase-admin/auth";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";

const db = getFirestore();
const resendApiKey = defineSecret("RESEND_API_KEY");
const welcomeFromEmail = defineString("WELCOME_FROM_EMAIL");
const DAY_MS = 24 * 60 * 60 * 1000;
const REMINDER_START_DAY = 30;
const LOCK_DAY = 45;
const DEFAULT_TIME_ZONE = "Asia/Kolkata";

function requireGoogleUser(request: Parameters<Parameters<typeof onCall>[1]>[0]): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in is required.");
  if (request.auth?.token.firebase?.sign_in_provider !== "google.com") {
    throw new HttpsError("permission-denied", "A Google-authenticated session is required.");
  }
  return uid;
}

function safeTimeZone(value: unknown): string {
  if (typeof value !== "string" || value.length > 64) return DEFAULT_TIME_ZONE;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: value });
    return value;
  } catch {
    return DEFAULT_TIME_ZONE;
  }
}

function localParts(timestamp: number, timeZone: string): { day: number; hour: number } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(timestamp));
  const get = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? "0");
  const day = Math.floor(Date.UTC(get("year"), get("month") - 1, get("day")) / DAY_MS);
  return { day, hour: get("hour") };
}

async function sendSubscriptionEmail(uid: string): Promise<void> {
  const user = await getAuth().getUser(uid);
  const email = user.email;
  const verifiedGoogle = user.emailVerified &&
    user.providerData.some((provider) => provider.providerId === "google.com");
  if (!email || !verifiedGoogle) return;

  const from = welcomeFromEmail.value().trim();
  const apiKey = resendApiKey.value();
  if (!from || !apiKey) throw new Error("Subscription email provider is not configured.");

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from,
      to: [email],
      subject: "Continue using SKYPLUS MONEY",
      text: "Your 30-day free trial is complete. You can continue using SKYPLUS MONEY during the 15-day reminder period by subscribing to the annual plan for ₹499. Your data remains safe if you choose to subscribe later.",
      html: "<p>Your 30-day free trial is complete.</p><p>You can continue using SKYPLUS MONEY during the 15-day reminder period by subscribing to the annual plan for <strong>₹499/year</strong>.</p><p>Your financial data remains safe if you choose to subscribe later.</p>",
    }),
  });
  if (!response.ok) throw new Error("Subscription email provider rejected the message.");
}

/**
 * Creates the one-time trial record on first authenticated activation.
 * The server timestamp is authoritative; reinstalling or clearing app data cannot restart it.
 */
export const startSubscriptionTrial = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireGoogleUser(request);
    const stateRef = db.collection("subscriptionStates").doc(uid);
    const requestedTimeZone = safeTimeZone(request.data?.timeZone);
    const result = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(stateRef);
      if (snapshot.exists) {
        return { created: false, state: snapshot.data() ?? {} };
      }
      const state = {
        uid,
        firstActivationAt: FieldValue.serverTimestamp(),
        timeZone: requestedTimeZone,
        accessState: "trial",
        entitlementState: "none",
        reminderDaysSent: [],
        finalNoticeSent: false,
        dataRetention: "retain",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      };
      transaction.create(stateRef, state);
      return { created: true, state };
    });
    return { created: result.created, firstActivationAt: result.state.firstActivationAt ?? null };
  },
);

/** Stores only the current user's FCM token and IANA time zone. */
export const registerSubscriptionPushToken = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireGoogleUser(request);
    const token = request.data?.token;
    if (typeof token !== "string" || token.length < 20 || token.length > 4096) {
      throw new HttpsError("invalid-argument", "A valid push token is required.");
    }
    const timeZone = safeTimeZone(request.data?.timeZone);
    const stateRef = db.collection("subscriptionStates").doc(uid);
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(stateRef);
      if (!snapshot.exists) {
        throw new HttpsError("failed-precondition", "Start the trial before registering notifications.");
      }
      transaction.set(stateRef, {
        pushToken: token,
        timeZone,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });
    return { registered: true };
  },
);

/** Returns server-owned subscription state; clients must not decide entitlement locally. */
export const getSubscriptionTrialState = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireGoogleUser(request);
    const snapshot = await db.collection("subscriptionStates").doc(uid).get();
    if (!snapshot.exists) return { started: false, accessState: "not_started" };
    const state = snapshot.data() ?? {};
    const activatedAt = state.firstActivationAt as Timestamp | undefined;
    if (!activatedAt) throw new HttpsError("failed-precondition", "Trial activation is incomplete.");
    const timeZone = safeTimeZone(state.timeZone);
    const dayIndex = localParts(Date.now(), timeZone).day -
      localParts(activatedAt.toMillis(), timeZone).day;
    const hasActiveEntitlement = state.entitlementState === "active";
    const accessState = hasActiveEntitlement
      ? "subscribed"
      : dayIndex < REMINDER_START_DAY
        ? "trial"
        : dayIndex < LOCK_DAY
          ? "reminder_window"
          : "locked";
    return {
      started: true,
      accessState,
      trialDay: dayIndex + 1,
      annualPriceMinor: 49900,
      currency: "INR",
      firstActivationAt: activatedAt.toMillis(),
    };
  },
);

/**
 * Runs hourly and delivers at most one reminder for each local trial day.
 * Day 30 also sends one outbound email; no email inbox access is used.
 */
export const deliverSubscriptionTrialReminders = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Etc/UTC", secrets: [resendApiKey] },
  async () => {
    const now = Date.now();
    const page = await db.collection("subscriptionStates")
      .where("accessState", "in", ["trial", "reminder_window"])
      .limit(500)
      .get();

    for (const document of page.docs) {
      const state = document.data();
      if (state.entitlementState === "active") continue;
      const activation = state.firstActivationAt as Timestamp | undefined;
      if (!activation) continue;
      const timeZone = safeTimeZone(state.timeZone);
      const localNow = localParts(now, timeZone);
      if (localNow.hour < 9) continue;
      const dayIndex = localNow.day - localParts(activation.toMillis(), timeZone).day;
      if (dayIndex < REMINDER_START_DAY || dayIndex > LOCK_DAY) continue;

      const uid = document.id;
      const dayKey = String(dayIndex + 1);
      const finalNotice = dayIndex >= LOCK_DAY;
      const claim = await db.runTransaction(async (transaction) => {
        const fresh = await transaction.get(document.ref);
        const current = fresh.data() ?? {};
        if (current.entitlementState === "active") return false;
        const sentDays = Array.isArray(current.reminderDaysSent)
          ? current.reminderDaysSent as number[]
          : [];
        if (finalNotice ? current.finalNoticeSent === true : sentDays.includes(dayIndex + 1)) {
          return false;
        }
        transaction.set(document.ref, finalNotice
          ? { finalNoticeSent: true, accessState: "locked", updatedAt: FieldValue.serverTimestamp() }
          : {
              reminderDaysSent: FieldValue.arrayUnion(dayIndex + 1),
              accessState: "reminder_window",
              updatedAt: FieldValue.serverTimestamp(),
            }, { merge: true });
        return true;
      });
      if (!claim) continue;

      const token = state.pushToken;
      if (typeof token === "string" && token.length > 0) {
        try {
          await getMessaging().send({
            token,
            notification: finalNotice
              ? {
                  title: "SKYPLUS MONEY access paused",
                  body: "Your trial and reminder period have ended. Subscribe anytime to restore access to your saved data.",
                }
              : {
                  title: "Continue with SKYPLUS MONEY",
                  body: "Your free access is continuing during the reminder period. Subscribe for ₹499/year to keep using all features.",
                },
            data: { type: "subscription", destination: "subscription", trialDay: dayKey },
            android: { priority: "normal" },
          });
        } catch (error) {
          const code = (error as { errorInfo?: { code?: string } }).errorInfo?.code;
          if (code === "messaging/registration-token-not-registered" ||
              code === "messaging/invalid-registration-token") {
            await document.ref.set({ pushToken: FieldValue.delete() }, { merge: true });
          }
        }
      }

      if (dayIndex === REMINDER_START_DAY) {
        try {
          await sendSubscriptionEmail(uid);
          await document.ref.set({
            subscriptionEmailDay30: "sent",
            subscriptionEmailSentAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        } catch {
          await document.ref.set({
            subscriptionEmailDay30: "failed",
            subscriptionEmailAttemptedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
        }
      }
    }
  },
);
