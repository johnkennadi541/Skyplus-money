import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();

const db = getFirestore();
const resendApiKey = defineSecret("RESEND_API_KEY");
const welcomeFromEmail = defineString("WELCOME_FROM_EMAIL");
const PENDING_RETRY_WINDOW_MS = 5 * 60 * 1000;

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return entities[character] ?? character;
  });
}

/**
 * Sends one welcome email to the authenticated Google account address.
 * The client cannot choose a recipient, and this function has no inbox/read scope.
 */
export const sendWelcomeEmail = onCall(
  { secrets: [resendApiKey] },
  async (request) => {
    const uid = request.auth?.uid;
    const email = request.auth?.token.email;
    const emailVerified = request.auth?.token.email_verified === true;
    const signInProvider = request.auth?.token.firebase?.sign_in_provider;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in is required.");
    if (signInProvider !== "google.com") {
      throw new HttpsError("permission-denied", "A Google-authenticated session is required.");
    }
    if (typeof email !== "string" || !emailVerified) {
      throw new HttpsError("failed-precondition", "A verified Google email address is required.");
    }

    const rawFirstName = request.data?.firstName;
    if (typeof rawFirstName !== "string") {
      throw new HttpsError("invalid-argument", "Profile first name is required.");
    }
    const firstName = rawFirstName.trim();
    if (!firstName || firstName.length > 60) {
      throw new HttpsError("invalid-argument", "Profile first name is invalid.");
    }

    const deliveryRef = db.collection("welcomeEmailDeliveries").doc(uid);
    const now = Date.now();
    const shouldSend = await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(deliveryRef);
      if (existing.get("status") === "sent") return false;
      const updatedAt = existing.get("attemptedAt")?.toMillis?.() ?? 0;
      if (existing.get("status") === "sending" && now - updatedAt < PENDING_RETRY_WINDOW_MS) {
        return false;
      }
      transaction.set(
        deliveryRef,
        {
          status: "sending",
          attemptedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      return true;
    });

    if (!shouldSend) return { sent: false, reason: "already_sent_or_pending" };

    const htmlName = escapeHtml(firstName);
    const from = welcomeFromEmail.value().trim();
    const apiKey = resendApiKey.value();
    if (!from || !apiKey) {
      await deliveryRef.set({ status: "failed", failedAt: FieldValue.serverTimestamp() }, { merge: true });
      throw new HttpsError("failed-precondition", "Welcome email provider is not configured.");
    }

    try {
      const response = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          from,
          to: [email],
          subject: "Welcome to SKYPLUS MONEY",
          text: `Hi ${firstName}, welcome to SKYPLUS MONEY. Your secure personal finance workspace is ready.`,
          html: `<p>Hi ${htmlName},</p><p>Welcome to <strong>SKYPLUS MONEY</strong>.</p><p>Your secure personal finance workspace is ready.</p>`,
        }),
      });

      if (!response.ok) {
        await deliveryRef.set({ status: "failed", failedAt: FieldValue.serverTimestamp() }, { merge: true });
        throw new HttpsError("internal", "Welcome email delivery failed.");
      }

      await deliveryRef.set(
        { status: "sent", sentAt: FieldValue.serverTimestamp() },
        { merge: true },
      );
      return { sent: true };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      await deliveryRef.set({ status: "failed", failedAt: FieldValue.serverTimestamp() }, { merge: true });
      throw new HttpsError("internal", "Welcome email delivery failed.");
    }
  },
);
