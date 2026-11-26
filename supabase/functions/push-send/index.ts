// Supabase Edge Function: push-send
//
// Sends a Firebase Cloud Messaging push to every registered device so a
// dashboard notification reaches the phone even when the app has been closed.
// Supabase Realtime covers the "app is running" case; this covers the rest.
//
// It reads the device FCM tokens straight from the `devices` table using the
// service-role key (injected by Supabase), signs a Google OAuth assertion with
// the Firebase service account (stored as the FIREBASE_SERVICE_ACCOUNT secret),
// and posts to the FCM HTTP v1 API. No billing, no Cloud Functions.

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

function b64url(input: string | ArrayBuffer): string {
  let bytes: Uint8Array;
  if (typeof input === "string") {
    bytes = new TextEncoder().encode(input);
  } else {
    bytes = new Uint8Array(input);
  }
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function googleAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const data = `${header}.${claims}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(data));
  const jwt = `${data}.${b64url(sig)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const json = await res.json();
  if (!json.access_token) throw new Error(`token exchange failed: ${JSON.stringify(json)}`);
  return json.access_token as string;
}

async function deviceTokens(): Promise<string[]> {
  const url = `${Deno.env.get("SUPABASE_URL")}/rest/v1/devices?select=fcm_token&fcm_token=not.is.null`;
  const res = await fetch(url, {
    headers: {
      apikey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!}`,
    },
  });
  const rows = await res.json();
  if (!Array.isArray(rows)) return [];
  return rows.map((r: { fcm_token?: string }) => r.fcm_token).filter((t): t is string => !!t);
}

async function sendOne(accessToken: string, projectId: string, fcmToken: string, data: Record<string, unknown>) {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token: fcmToken,
        notification: {
          title: String(data.title ?? "تنبيه"),
          body: String(data.body ?? ""),
        },
        data: {
          id: String(data.id ?? ""),
          type: String(data.type ?? "text"),
          title: String(data.title ?? ""),
          body: String(data.body ?? ""),
          audio_url: String(data.audio_url ?? ""),
        },
        android: {
          priority: "high",
          notification: {
            channel_id: "athkar_alerts_channel",
            sound: "default",
            notification_priority: "PRIORITY_HIGH",
            default_vibrate_timings: true,
          },
        },
      },
    }),
  });
  return res.ok;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("POST only", { status: 405 });
  }
  try {
    const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!) as ServiceAccount;
    const payload = await req.json();

    const accessToken = await googleAccessToken(sa);
    const tokens = await deviceTokens();

    const results = await Promise.allSettled(
      tokens.map((t) => sendOne(accessToken, sa.project_id, t, payload)),
    );
    const sent = results.filter((r) => r.status === "fulfilled" && r.value).length;

    return new Response(JSON.stringify({ attempted: tokens.length, sent }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
