/* Al-Muezzin admin dashboard — Supabase edition (free tier, no Firebase).
 *
 *  - Devices:       the Android app upserts a row in `devices`.
 *  - Sends:         the dashboard INSERTs a row in `notifications`.
 *                   Supabase Realtime broadcasts it to every connected app
 *                   instantly; offline apps catch up over the REST API.
 *  - Audio:         direct URL, or uploaded to a free file host.
 *  - Auth:          Supabase Auth (email + password) gates the dashboard.
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const cfg = window.SUPABASE_CONFIG || null;

  let sb = null;            // supabase client
  let devices = [];

  const LOCAL_HISTORY = "almuezzin_dashboard_history";
  const CATBOX_URL = "https://catbox.moe/user/api.php";

  // ---------------------------------------------------------------- utilities
  function toast(message, isError) {
    const el = $("toast");
    if (!el) return;
    el.textContent = message;
    el.style.background = isError ? "var(--danger)" : "var(--accent)";
    el.style.color = isError ? "#2a0910" : "#06221f";
    el.classList.remove("hidden");
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.add("hidden"), 3200);
  }

  function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return isNaN(date) ? "—" : date.toLocaleString("ar-EG", { dateStyle: "medium", timeStyle: "short" });
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

  // ------------------------------------------------------------------- config
  function initSupabase() {
    if (!cfg || !cfg.url || !cfg.anonKey || /YOUR_/.test(cfg.anonKey + cfg.url)) {
      $("loginView").classList.add("hidden");
      $("setupView").classList.remove("hidden");
      return false;
    }
    if (!window.supabase || !window.supabase.createClient) {
      $("setupView").classList.remove("hidden");
      $("setupMsg").textContent = "تعذّر تحميل مكتبة Supabase — تحقق من الاتصال بالإنترنت.";
      return false;
    }
    sb = window.supabase.createClient(cfg.url, cfg.anonKey);
    bindAuth();
    return true;
  }

  function bindAuth() {
    sb.auth.onAuthStateChange((_event, session) => {
      const user = session && session.user;
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
    sb.auth.getSession().then(({ data }) => {
      if (!data || !data.session) return;
    });
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
      const { error } = await sb.auth.signInWithPassword({ email, password });
      if (error) {
        $("loginError").textContent = "فشل الدخول: " + error.message;
      } else {
        $("password").value = "";
      }
    });

    $("logoutBtn").addEventListener("click", async () => {
      await sb.auth.signOut();
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

  /** Backend-free, instant delivery to the admin's own phone. */
  function buildDeepLink(type, title, body, audioUrl) {
    const reminder = {
      id: "rem_" + Date.now(),
      title,
      body,
      reminderKind: type,
      persistenceMode: "pinned",
      deliveryMode: "now",
      scheduledTimeHHmm: "08:00",
      isActive: true,
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
    pushLocalHistory({ type, title, body, audioUrl: directUrl, sentAt: Date.now(), recipients: 1, local: true });
    window.location.href = buildDeepLink(type, title, body, directUrl);
    toast("تم فتح التطبيق — إن لم يعمل الرابط على الكمبيوتر، انسخه وافتحه على الهاتف");
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

    const button = $("sendBtn");
    button.disabled = true;
    $("sendStatus").textContent = "جارٍ الإرسال…";
    $("sendHint").textContent = "";

    try {
      let audioUrl = directUrl;
      if (type === "audio" && file) {
        $("sendStatus").textContent = "جارٍ رفع الملف إلى catbox.moe…";
        audioUrl = await uploadAudio(file);
      }

      const recipients = (target === "all")
        ? devices.length
        : devices.filter((d) => d.id === target).length;

      const row = {
        type,
        title,
        body,
        audio_url: audioUrl || null,
        target: target === "all" ? null : target,
        recipients,
        sent_by: (sb.auth.getUser && (await sb.auth.getUser()).data?.user?.email) || "admin"
      };

      const { data, error } = await sb.from("notifications").insert(row).select().single();
      if (error) throw new Error(error.message);

      // Ask the push-send Edge Function to deliver via Firebase Cloud Messaging
      // so the notification reaches devices even when the app is closed.
      let fcmSent = 0;
      try {
        const { data: pushResp } = await sb.functions.invoke("push-send", {
          body: { id: data?.id, type, title, body, audio_url: audioUrl || "" }
        });
        fcmSent = pushResp?.sent || 0;
      } catch (e) {
        console.warn("FCM push-send failed:", e);
      }

      // Realtime delivers the INSERT to every connected app instantly.
      $("sendStatus").textContent =
        "✅ تم الإرسال — وصل إلى " + (fcmSent || recipients || "كل") + " جهاز فوراً (حتى المغلق)." +
        " الأجهزة غير المتصلة ستستلم عند فتحها.";
      toast("تم إرسال الإشعار بنجاح");
      pushLocalHistory({ type, title, body, audioUrl: audioUrl || "", sentAt: Date.now(), recipients });
      $("pushBody").value = "";
      $("audioFile").value = "";
      renderHistory(readLocal(LOCAL_HISTORY));
      loadHistory();
    } catch (error) {
      $("sendStatus").textContent = "";
      $("sendHint").textContent = "فشل الإرسال: " + (error.message || error);
      toast("فشل الإرسال", true);
    } finally {
      button.disabled = false;
    }
  }

  // ------------------------------------------------------------------ devices
  async function loadDevices() {
    if (!sb) return;
    const { data, error } = await sb
      .from("devices")
      .select("*")
      .order("last_seen_at", { ascending: false })
      .limit(500);
    if (error) {
      devices = readLocal("almuezzin_dashboard_devices");
      renderDevices();
      $("sendHint").textContent = "تعذّر قراءة الأجهزة: " + error.message;
      return;
    }
    devices = (data || []).map((d) => ({
      id: d.id,
      token: d.id,
      appVersion: d.app_version,
      platform: d.platform,
      model: d.model,
      lastSeenAt: d.last_seen_at
    }));
    writeLocal("almuezzin_dashboard_devices", devices);
    renderDevices();
    $("sendHint").textContent = "";
  }

  function renderDevices() {
    const rows = $("deviceRows");
    rows.innerHTML = "";
    $("deviceCount").textContent = devices.length;
    devices.forEach((device) => {
      const tr = document.createElement("tr");
      const id = document.createElement("td");
      id.textContent = (device.id || "").slice(0, 22) + "…";
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
      const option = document.createElement("option");
      option.value = device.id;
      option.textContent = "جهاز " + (device.id || "").slice(0, 12) + "… (" + (device.appVersion || "?") + ")";
      select.appendChild(option);
    });
  }

  // ------------------------------------------------------------------ history
  async function loadHistory() {
    if (!sb) return;
    const { data, error } = await sb
      .from("notifications")
      .select("*")
      .order("sent_at", { ascending: false })
      .limit(50);
    if (error) {
      renderHistory(readLocal(LOCAL_HISTORY));
      return;
    }
    renderHistory((data || []).map((n) => ({
      type: n.type,
      title: n.title,
      body: n.body,
      audioUrl: n.audio_url,
      sentAt: n.sent_at,
      recipients: n.recipients
    })));
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
        "<span>" + (item.type === "audio" ? "🔊 صوتي" : "💬 نصي") + " · " + formatDate(item.sentAt) +
        (item.local ? " · محلي" : "") + "</span>" +
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
    initSupabase();
  });
})();