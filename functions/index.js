/**
 * Al-Muezzin push backend.
 *
 *  - registerDevice : called by the Android app when it gets an FCM token.
 *  - sendNotification: called by the admin dashboard (Firebase Auth required).
 *  - listDevices    : called by the admin dashboard to show registered devices.
 */
const { onRequest, onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const DEVICES = "devices";
const NOTIFICATIONS = "notifications";

/**
 * POST /registerDevice
 * Body: { token: string, appVersion?: string, platform?: string }
 */
exports.registerDevice = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const token = (req.body && req.body.token) || "";
  if (!token || typeof token !== "string" || token.length < 20) {
    res.status(400).json({ error: "Invalid FCM token" });
    return;
  }

  const payload = {
    token,
    appVersion: (req.body && req.body.appVersion) || "unknown",
    platform: (req.body && req.body.platform) || "android",
    lastSeenAt: admin.firestore.Timestamp.now()
  };

  try {
    await db.collection(DEVICES).doc(encodeToken(token)).set(payload, { merge: true });
    logger.info("Device registered", { appVersion: payload.appVersion });
    res.status(200).json({ ok: true });
  } catch (error) {
    logger.error("registerDevice failed", error);
    res.status(500).json({ error: "Registration failed" });
  }
});

/**
 * Callable: sendNotification
 * Data: {
 *   type: "text" | "audio",
 *   title: string,
 *   body: string,
 *   audioUrl?: string,
 *   targetTokens?: string[]   // when omitted -> every registered device
 * }
 */
exports.sendNotification = onCall(async (request) => {
  if (!request.auth || !request.auth.token) {
    throw new Error("unauthenticated");
  }

  const data = request.data || {};
  const type = data.type === "audio" ? "audio" : "text";
  const title = String(data.title || "تنبيه من لوحة التحكم").slice(0, 120);
  const body = String(data.body || "").slice(0, 600);
  const audioUrl = String(data.audioUrl || "").slice(0, 2000);
  const targetTokens = Array.isArray(data.targetTokens)
    ? data.targetTokens.filter((t) => typeof t === "string" && t.length > 20)
    : [];

  if (type === "audio" && !audioUrl) {
    throw new Error("audioUrl is required for audio notifications");
  }

  let tokens = targetTokens;
  if (tokens.length === 0) {
    const snapshot = await db.collection(DEVICES).select().get();
    tokens = snapshot.docs.map((doc) => doc.data().token).filter(Boolean);
  }

  if (tokens.length === 0) {
    throw new Error("no registered devices");
  }

  const message = {
    data: {
      type,
      title,
      body,
      audioUrl,
      reminderId: `push_${Date.now()}`
    },
    android: {
      priority: "high",
      notification: type === "text"
        ? { channelId: "push_text_channel", title, body }
        : { channelId: "push_audio_channel", title, body }
    }
  };

  const result = await admin.messaging().sendEachForMulticast({
    tokens,
    data: message.data,
    android: message.android
  });

  // Prune tokens that Firebase reported as permanently invalid.
  const invalid = [];
  result.responses.forEach((resp, index) => {
    if (!resp.success && resp.error && resp.error.code === "messaging/registration-token-not-registered") {
      invalid.push(tokens[index]);
    }
  });
  await Promise.all(
    invalid.map((token) => db.collection(DEVICES).doc(encodeToken(token)).delete().catch(() => null))
  );

  const history = {
    type,
    title,
    body,
    audioUrl,
    sentAt: admin.firestore.Timestamp.now(),
    recipients: result.successCount,
    failed: result.failureCount,
    sender: request.auth.token.email || "admin"
  };
  await db.collection(NOTIFICATIONS).add(history);

  logger.info("Notification sent", { type, recipients: result.successCount });
  return {
    ok: true,
    successCount: result.successCount,
    failureCount: result.failureCount,
    cleaned: invalid.length
  };
});

/**
 * Callable: listDevices — registered devices with their last seen time.
 */
exports.listDevices = onCall(async (request) => {
  if (!request.auth || !request.auth.token) {
    throw new Error("unauthenticated");
  }
  const snapshot = await db.collection(DEVICES).orderBy("lastSeenAt", "desc").limit(500).get();
  return {
    devices: snapshot.docs.map((doc) => {
      const data = doc.data();
      return {
        id: doc.id,
        appVersion: data.appVersion || "unknown",
        platform: data.platform || "android",
        lastSeenAt: data.lastSeenAt ? data.lastSeenAt.toDate().toISOString() : null
      };
    })
  };
});

function encodeToken(token) {
  return Buffer.from(token, "utf8").toString("base64url").slice(0, 180);
}
