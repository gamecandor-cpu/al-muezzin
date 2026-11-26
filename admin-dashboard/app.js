/* Al-Muezzin admin dashboard — Spark-plan friendly edition.
 *
 *  - Devices: registered by the Android app directly into Firebase Realtime
 *    Database (free on Spark). The dashboard authenticates with Firebase Auth,
 *    RTDB rules then expose the data to admin users.
 *  - Sends:   FCM HTTP v1 API called straight from the browser using an OAuth2
 *    access token minted locally from the admin's service-account JSON
 *    (loaded from a file picker or pasted manually). The service-account is
 *    kept only in `localStorage` of this browser; nothing is uploaded.
 *  - Audio upload: catbox.moe free file host (no API key, ~200MB files).
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const config = window.FIREBASE_CONFIG || null;
  const RTDB_URL = "https://gen-lang-client-0088673365-default-rtdb.firebaseio.com";
  const FCM_PROJECT = (config && (config.projectId || (config.appId || "").split(":")[1])) || "gen-lang-client-0088673365";
  const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
  const TOKEN_URL = "https://oauth2.googleapis.com/token";
  const FCM_SEND_URL = `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT}/messages:send`;
  const CATBOX_URL = "https://catbox.moe/user/api.php";

  let auth = null;
  let db = null;
  let devices = [];
  let sa = null;            // service-account JSON
  let accessToken = null;   // short-lived OAuth2 access token for FCM
  let accessTokenExpiry = 0;

  const LOCAL_HISTORY = "almuezzin_dashboard_history";
  const LOCAL_SA = "almuezzin_service_account";

  // ---------------------------------------------------------------- utilities
  function toast(message, isError) {
    const el = $("toast");
    el.textContent = message;
    el.style.background = isError ? "var(--danger)" : "var(--accent)";
    el.style.color = isError ? "#2a0910" : "#06221f";
    el.classList.remove("hidden");
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.add("hidden"), 3200);
  }

  function formatDate(value) {
    if (value === undefined || value === null) return "—";
    const n = typeof value === "number" ? value : Number(value);
    if (!isFinite(n)) return String(value);
    return new Date(n).toLocaleString("ar-EG", { dateStyle: "medium", timeStyle: "short" });
  }

  function readLocal(key) {
    try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch (e) { return []; }
  }

  function writeLocal(key, items) {
    try { localStorage.setItem(key, JSON.stringify(items.slice(0, 100))); } catch (e) { /* ignore */ }
  }

  function pushLocalHistory(entry) {
    const items = readLocal(LOCAL_HISTORY);
    items.unshift(entry);
    writeLocal(LOCAL_HISTORY, items);
  }

  // ------------------------------------------------------------ service-account
  function decodeBase64Url(b64url) {
    const b64 = b64url.replace(/-/g, "+").replace(/_/g, "/");
    const pad = "=".repeat((4 - (b64.length % 4)) % 4);
    return atob(b64 + pad);
  }

  function pemToArrayBuffer(pem) {
    const cleaned = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "")
      .replace(/-----END PRIVATE KEY-----/g, "")
      .replace(/\s+/g, "");
    const binary = atob(cleaned);
    const buffer = new ArrayBuffer(binary.length);
    const view = new Uint8Array(buffer);
    for (let i = 0; i < binary.length; i++) view[i] = binary.charCodeAt(i);
    return buffer;
  }

  async function importPrivateKey(pkcs8Pem) {
    return crypto.subtle.importKey(
      "pkcs8",
      pemToArrayBuffer(pkcs8Pem),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"]
    );
  }

  function base64UrlEncode(bytes) {
    let str = "";
    const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
    for (let i = 0; i < view.length; i++) str += String.fromCharCode(view[i]);
    return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  async function signJwt(sa) {
    const header = { alg: "RS256", typ: "JWT" };
    const now = Math.floor(Date.now() / 1000);
    const claim = {
      iss: sa.client_email,
      scope: FCM_SCOPE,
      aud: TOKEN_URL,
      iat: now,
      exp: now + 3500
    };
    const enc = (obj) => base64UrlEncode(new TextEncoder().encode(JSON.stringify(obj)));
    const signingInput = enc(header) + "." + enc(claim);
    const key = await importPrivateKey(sa.private_key);
    const signature = await crypto.subtle.sign(
      { name: "RSASSA-PKCS1-v1_5" },
      key,
      new TextEncoder().encode(signingInput)
    );
    return signingInput + "." + base64UrlEncode(signature);
  }

  async function fetchAccessToken(forceRefresh) {
    if (!sa) throw new Error("لم يتم رفع مفتاح الخدمة بعد.");
    if (!forceRefresh && accessToken && Date.now() < accessTokenExpiry - 60_000) {
      return accessToken;
    }
    const jwt = await signJwt(sa);
    const res = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + encodeURIComponent(jwt)
    });
    if (!res.ok) throw new Error("OAuth2 HTTP " + res.status + ": " + (await res.text()));
    const data = await res.json();
    accessToken = data.access_token;
    accessTokenExpiry = Date.now() + (data.expires_in || 3600) * 1000;
    return accessToken;
  }

  function loadStoredServiceAccount() {
    try {
      const raw = localStorage.getItem(LOCAL_SA);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch (e) { return null; }
  }

  function storeServiceAccount(obj) {
    sa = obj;
    accessToken = null;
    accessTokenExpiry = 0;
    localStorage.setItem(LOCAL_SA, JSON.stringify(obj));
    $("saStatus").textContent = "✔ مفتاح FCM v1 محفوظ في المتصفح فقط (" + (obj.project_id || obj.projectId) + ")";
  }

  // ------------------------------------------------------------------- wiring
  function initFirebase() {
    if (!config) {
      $("loginView").classList.add("hidden");
      $("setupView").classList.remove("hidden");
      return false;
    }
    firebase.initializeApp(config, { databaseURL: RTDB_URL });
    auth = firebase.auth();
    db = firebase.database();

    sa = loadStoredServiceAccount();
    $("saStatus").textContent = sa
      ? "✔ مفتاح FCM v1 محفوظ في المتصفح فقط (" + sa.project_id + ")"
      : "⚠ لم يُرفع مفتاح FCM v1 بعد — زر «إرسال» لن يعمل بدونه (الإرسال الفوري عبر الرابط يعمل دائماً).";

    auth.onAuthStateChanged((user) => {
      if (user) {
        $("loginView").classList.add("hidden");
        $("setupView").classList.add("hidden");
        $("appView").classList.remove("hidden");
        $("sessionUser").textContent = user.email || "مدير";
        $("logoutBtn").classList.remove("hidden");
        loadDevices();
        loadHistory();
      } else {
        $("setupView").classList.add("hidden");
        $("loginView").classList.remove("hidden");
        $("appView").classList.add("hidden");
        $("sessionUser").textContent = "غير مسجّل";
        $("logoutBtn").classList.add("hidden");
      }
    });
    return true;
  }

  // -------------------------------------------------------------------- login
  function bindLogin() {
    $("loginBtn").addEventListener("click", async () => {
      $("loginError").textContent = "";
      const email = $("email").value.trim();
      const password = $("password").value;
      if (!email || !password) {
        $("loginError").textContent = "أدخل البريد وكلمة المرور";
        return;
      }
      try {
        await auth.signInWithEmailAndPassword(email, password);
        $("password").value = "";
      } catch (error) {
        $("loginError").textContent = "فشل الدخول: " + (error && error.message ? error.message : error);
      }
    });

    $("logoutBtn").addEventListener("click", () => auth.signOut());

    $("saFile").addEventListener("change", async (event) => {
      const file = event.target.files[0];
      if (!file) return;
      try {
        const text = await file.text();
        storeServiceAccount(JSON.parse(text));
      } catch (error) {
        $("saStatus").textContent = "⚠ فشل قراءة الملف: " + error.message;
      }
    });

    $("saPasteBtn").addEventListener("click", () => {
      const text = $("saPaste").value.trim();
      if (!text) return;
      try {
        storeServiceAccount(JSON.parse(text));
        $("saPaste").value = "";
      } catch (error) {
        $("saStatus").textContent = "⚠ المحتوى ليس JSON صالحاً: " + error.message;
      }
    });
  }

  // -------------------------------------------------------------------- tabs
  function bindTabs() {
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
        tab.classList.add("active");
        ["compose", "devices", "history"].forEach((name) => {
          $("tab-" + name).classList.toggle("hidden", name !== tab.dataset.tab);
        });
      });
    });
  }

  // ------------------------------------------------------------ push composer
  function bindComposer() {
    document.querySelectorAll('input[name="pushType"]').forEach((radio) => {
      radio.addEventListener("change", () => {
        const selected = document.querySelector('input[name="pushType"]:checked').value;
        $("audioBlock").classList.toggle("hidden", selected !== "audio");
      });
    });

    $("sendBtn").addEventListener("click", sendNotification);
    $("sendLocalBtn").addEventListener("click", sendViaDeepLink);
  }

  function buildDeepLink(type, title, body, audioUrl) {
    const reminder = {
      id: "rem_" + Date.now(),
      title, body, reminderKind: type, persistenceMode: "pinned",
      deliveryMode: "now", scheduledTimeHHmm: "08:00", isActive: true,
      audioUrl: audioUrl || ""
    };
    const json = JSON.stringify({ reminders: [reminder] });
    const encoded = btoa(unescape(encodeURIComponent(json)))
      .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    return "almuezzin://sync?d=" + encoded;
  }

  function sendViaDeepLink() {
    const type = document.querySelector('input[name="pushType"]:checked').value;
    const title = $("pushTitle").value.trim();
    const body = $("pushBody").value.trim();
    const directUrl = $("audioUrl").value.trim();
    if (!title) { toast("اكتب عنوان الإشعار", true); return; }
    const link = buildDeepLink(type, title, body, directUrl);
    pushLocalHistory({ type, title, body, audioUrl: directUrl, sentAt: Date.now(), recipients: 1, local: true });
    window.location.href = link;
    toast("تم فتح التطبيق — إن لم يعمل الرابط على كمبيوتر، انسخه وافتحه على الهاتف");
    renderHistory(readLocal(LOCAL_HISTORY));
  }

  async function uploadAudio(file) {
    const fd = new FormData();
    fd.append("reqtype", "fileupload");
    fd.append("fileToUpload", file, file.name);
    const res = await fetch(CATBOX_URL, { method: "POST", body: fd });
    if (!res.ok) throw new Error("رفع الملف فشل HTTP " + res.status);
    return (await res.text()).trim();
  }

  async function sendOne(target, dataFields) {
    const url = FCM_SEND_URL;
    let token_res;
    try {
      token_res = await fetchAccessToken();
    } catch (err) {
      throw new Error("تعذّر استخراج توكن OAuth2: " + err.message);
    }
    const message = { data: dataFields };
    if (target && target.topic) message.topic = target.topic;
    else message.token = target && target.token ? target.token : "";
    const body = { message };
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + token_res,
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const text = await res.text();
      // Token expired mid-flight → retry once.
      if (res.status === 401) {
        accessToken = null;
        return sendOne(target, dataFields);
      }
      throw new Error("FCM v1 HTTP " + res.status + ": " + text.slice(0, 200));
    }
    return res.json();
  }

  async function sendNotification() {
    const type = document.querySelector('input[name="pushType"]:checked').value;
    const title = $("pushTitle").value.trim();
    const body = $("pushBody").value.trim();
    const target = $("target").value;
    const file = $("audioFile").files[0];
    const directUrl = $("audioUrl").value.trim();

    if (!title) { toast("اكتب عنوان الإشعار", true); return; }
    if (type === "audio" && !file && !directUrl) {
      toast("اختر ملف صوت أو ضع رابطاً مباشراً", true);
      return;
    }
    if (!sa) {
      toast("ارفع مفتاح FCM v1 (تفاصيل أسفل زر الدخول) أولاً.", true);
      return;
    }

    const button = $("sendBtn");
    button.disabled = true;
    $("sendStatus").textContent = "جارٍ الإرسال…";

    try {
      let audioUrl = directUrl;
      if (type === "audio" && file) {
        $("sendStatus").textContent = "جارٍ رفع الملف إلى catbox.moe…";
        audioUrl = await uploadAudio(file);
      }

      const dataFields = {
        type, title, body, audioUrl: audioUrl || "",
        reminderId: "rem_" + Date.now()
      };

      let targets = devices;
      if (target !== "all") targets = devices.filter((d) => d.token === target);
      // Always include subscribtion to topic "all" so devices not in /devices
      // (e.g. older installs) still receive a broadcast.
      const fallbackBroadcast = target === "all";

      let successCount = 0;
      let lastError = null;
      for (const device of targets) {
        if (!device.token) continue;
        try {
          await sendOne({ token: device.token }, dataFields);
          successCount++;
        } catch (e) {
          lastError = e;
          console.warn("FCM send failed for", device.id, e.message);
        }
      }
      // Broadcast to the shared topic so devices that are not in /devices
      // (or were captured after the snapshot) also receive the push.
      if (fallbackBroadcast) {
        try {
          await sendOne({ topic: "all" }, dataFields);
          successCount++;
        } catch (e) {
          lastError = e;
          console.warn("FCM topic broadcast failed", e.message);
        }
      }

      $("sendStatus").textContent =
        "تم الإرسال إلى " + successCount + " من " + targets.length + " جهاز." +
        (lastError ? " آخر خطأ: " + lastError.message : "");
      toast(successCount > 0 ? "تم إرسال الإشعار" : "لم يصل الإشعار لأحد — راجع الـconsole", successCount > 0);

      const notifRef = db.ref("notifications").push();
      await notifRef.set({
        type, title, body,
        audioUrl: audioUrl || "",
        sentAt: firebase.database.ServerValue.TIMESTAMP,
        sentBy: auth.currentUser && auth.currentUser.email,
        recipients: successCount,
      });

      pushLocalHistory({ type, title, body, audioUrl: audioUrl || "", sentAt: Date.now(), recipients: successCount });
      $("pushBody").value = "";
      $("audioFile").value = "";
      renderHistory(readLocal(LOCAL_HISTORY));
      loadHistory();
    } catch (error) {
      $("sendStatus").textContent = "";
      toast("فشل الإرسال: " + (error.message || error), true);
    } finally {
      button.disabled = false;
    }
  }

  // ------------------------------------------------------------------ devices
  async function loadDevices() {
    if (!db) return;
    try {
      const snapshot = await db.ref("devices").orderByChild("lastSeenAt").limitToLast(500).once("value");
      const items = [];
      snapshot.forEach((child) => items.push({ id: child.key, ...child.val() }));
      items.sort((a, b) => (b.lastSeenAt || 0) - (a.lastSeenAt || 0));
      devices = items;
      writeLocal(almuezzinBackup(), items);
      renderDevices();
      $("backendHint").textContent = "";
    } catch (error) {
      devices = readLocal(almuezzinBackup());
      renderDevices();
      $("backendHint").textContent = "تعذّر قراءة RTDB: " + error.message;
    }
  }

  function almuezzinBackup() { return "almuezzin_dashboard_devices"; }

  function renderDevices() {
    const rows = $("deviceRows");
    rows.innerHTML = "";
    $("deviceCount").textContent = devices.length;
    devices.forEach((device) => {
      const tr = document.createElement("tr");
      const id = document.createElement("td");
      id.textContent = (device.id || "").slice(0, 18) + "…";
      const version = document.createElement("td");
      version.textContent = device.appVersion || "—";
      const platform = document.createElement("td");
      platform.textContent = device.platform || "android";
      const model = document.createElement("td");
      model.textContent = device.model || "—";
      const seen = document.createElement("td");
      seen.textContent = formatDate(device.lastSeenAt);
      tr.append(id, version, platform, model, seen);
      rows.appendChild(tr);
    });

    const select = $("target");
    select.innerHTML = '<option value="all">كل الأجهزة المسجّلة</option>';
    devices.forEach((device) => {
      if (!device.token) return;
      const option = document.createElement("option");
      option.value = device.token;
      option.textContent = "جهاز " + (device.id || "").slice(0, 12) + "… (" + (device.appVersion || "?") + ")";
      select.appendChild(option);
    });
  }

  // ------------------------------------------------------------------ history
  async function loadHistory() {
    if (!db) return;
    try {
      const snap = await db.ref("notifications").orderByChild("sentAt").limitToLast(50).once("value");
      const items = [];
      snap.forEach((c) => items.push({ id: c.key, ...c.val() }));
      items.sort((a, b) => (b.sentAt || 0) - (a.sentAt || 0));
      renderHistory(items);
    } catch (error) {
      renderHistory(readLocal(LOCAL_HISTORY));
    }
  }

  function renderHistory(items) {
    const list = $("historyList");
    list.innerHTML = "";
    if (!items.length) {
      list.innerHTML = '<p class="muted">لا توجد إشعارات مرسلة بعد.</p>';
      return;
    }
    items.forEach((item) => {
      const wrapper = document.createElement("div");
      wrapper.className = "history-item";
      const header = document.createElement("header");
      header.innerHTML =
        "<span>" + (item.type === "audio" ? "🔊 صوتي" : "💬 نصي") + " · " +
        formatDate(item.sentAt) + (item.local ? " · محلي" : "") + "</span>" +
        "<span>أُرسل إلى " + (item.recipients || 0) + " جهاز</span>";
      const title = document.createElement("h3");
      title.textContent = item.title || "";
      const body = document.createElement("p");
      body.className = "muted";
      body.textContent = item.body || "";
      wrapper.append(header, title, body);
      if (item.audioUrl) {
        const audio = document.createElement("audio");
        audio.controls = true;
        audio.src = item.audioUrl;
        wrapper.appendChild(audio);
      }
      list.appendChild(wrapper);
    });
  }

  // --------------------------------------------------------------------- boot
  document.addEventListener("DOMContentLoaded", () => {
    bindTabs();
    bindLogin();
    bindComposer();
    initFirebase();
  });
})();
