/* Al-Muezzin admin dashboard — Firebase Cloud Messaging console. */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const config = window.FIREBASE_CONFIG || null;

  let auth = null;
  let db = null;
  let storage = null;
  let functions = null;
  let devices = [];

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
    if (!value) return "—";
    const date = value.toDate ? value.toDate() : new Date(value);
    return date.toLocaleString("ar-EG", { dateStyle: "medium", timeStyle: "short" });
  }

  // Local mirror of history/devices so the console keeps working even when
  // Firestore (or the Cloud Function backend) is not deployed yet.
  const LOCAL_HISTORY = "almuezzin_dashboard_history";
  const LOCAL_DEVICES = "almuezzin_dashboard_devices";

  function readLocal(key) {
    try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch (e) { return []; }
  }

  function writeLocal(key, items) {
    try { localStorage.setItem(key, JSON.stringify(items.slice(0, 100))); } catch (e) { /* ignore */ }
  }

  function saveLocalHistory(entry) {
    const items = readLocal(LOCAL_HISTORY);
    items.unshift(entry);
    writeLocal(LOCAL_HISTORY, items);
  }

  // ------------------------------------------------------------------- wiring
  function initFirebase() {
    if (!config) {
      $("loginView").classList.add("hidden");
      $("setupView").classList.remove("hidden");
      return false;
    }
    firebase.initializeApp(config);
    auth = firebase.auth();
    db = firebase.firestore();
    storage = firebase.storage();
    functions = firebase.functions();
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

  /**
   * Backend-free delivery: the admin's own phone opens the app directly with
   * the reminder payload (works without Cloud Functions or Firestore).
   */
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
    if (!title) {
      toast("اكتب عنوان الإشعار", true);
      return;
    }
    const link = buildDeepLink(type, title, body, directUrl);
    saveLocalHistory({ type, title, body, audioUrl: directUrl, sentAt: new Date().toISOString(), recipients: 1, local: true });
    window.location.href = link;
    toast("تم فتح التطبيق — إن لم يعمل الرابط على كمبيوتر، انسخ الرابط وافتحه على الهاتف");
    loadHistory();
  }

  async function uploadAudio(file) {
    const storageRef = storage.ref("notifications/" + Date.now() + "-" + file.name);
    await storageRef.put(file, { contentType: file.type });
    return await storageRef.getDownloadURL();
  }

  async function sendNotification() {
    const type = document.querySelector('input[name="pushType"]:checked').value;
    const title = $("pushTitle").value.trim();
    const body = $("pushBody").value.trim();
    const target = $("target").value;
    const file = $("audioFile").files[0];
    const directUrl = $("audioUrl").value.trim();

    if (!title) {
      toast("اكتب عنوان الإشعار", true);
      return;
    }
    if (type === "audio" && !file && !directUrl) {
      toast("اختر ملف صوت أو ضع رابطاً مباشراً", true);
      return;
    }

    const button = $("sendBtn");
    button.disabled = true;
    $("sendStatus").textContent = "جارٍ الإرسال…";

    try {
      let audioUrl = directUrl;
      if (type === "audio" && file) {
        $("sendStatus").textContent = "جارٍ رفع الملف…";
        audioUrl = await uploadAudio(file);
      }

      const payload = {
        type,
        title,
        body,
        audioUrl: audioUrl || "",
        targetTokens: target === "all" ? [] : [target]
      };

      const result = await functions.httpsCallable("sendNotification")(payload);
      $("sendStatus").textContent =
        "تم الإرسال إلى " + (result.data && result.data.successCount) + " جهاز.";
      $("backendHint").textContent = "";
      toast("تم إرسال الإشعار بنجاح");
      saveLocalHistory({
        type,
        title,
        body,
        audioUrl: audioUrl || "",
        sentAt: new Date().toISOString(),
        recipients: (result.data && result.data.successCount) || 0
      });
      $("pushBody").value = "";
      $("audioFile").value = "";
      loadHistory();
    } catch (error) {
      $("sendStatus").textContent = "";
      const message = error && error.message ? error.message : String(error);
      const backendDown = /internal|unavailable|not found|functions-error|Failed to fetch/i.test(message);
      $("backendHint").textContent = backendDown
        ? "لم يُنشر خادم Cloud Functions بعد (يتطلب ترقية المشروع إلى Blaze). استخدم زر «إرسال فوري لجهازي» مؤقتاً."
        : "فشل الإرسال: " + message;
      toast("فشل الإرسال عبر Firebase", true);
    } finally {
      button.disabled = false;
    }
  }

  // ------------------------------------------------------------------ devices
  async function loadDevices() {
    try {
      const snapshot = await db.collection("devices").orderBy("lastSeenAt", "desc").limit(500).get();
      devices = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      renderDevices();
    } catch (error) {
      devices = readLocal(LOCAL_DEVICES);
      renderDevices();
      $("backendHint").textContent =
        "تعذّر قراءة Firestore (" + error.message + ") — مشروع Firebase يحتاج ترقية Blaze أو نشر Cloud Functions.";
    }
  }

  function renderDevices() {
    const rows = $("deviceRows");
    rows.innerHTML = "";
    $("deviceCount").textContent = devices.length;
    devices.forEach((device) => {
      const tr = document.createElement("tr");
      const id = document.createElement("td");
      id.textContent = device.id.slice(0, 18) + "…";
      const version = document.createElement("td");
      version.textContent = device.appVersion || "—";
      const platform = document.createElement("td");
      platform.textContent = device.platform || "android";
      const seen = document.createElement("td");
      seen.textContent = formatDate(device.lastSeenAt);
      tr.append(id, version, platform, seen);
      rows.appendChild(tr);
    });

    const select = $("target");
    select.innerHTML = '<option value="all">كل الأجهزة المسجّلة</option>';
    devices.forEach((device) => {
      const option = document.createElement("option");
      option.value = device.token || "";
      option.textContent = "جهاز " + device.id.slice(0, 12) + "… (" + (device.appVersion || "?") + ")";
      if (device.token) select.appendChild(option);
    });
  }

  // ------------------------------------------------------------------ history
  async function loadHistory() {
    try {
      const snapshot = await db.collection("notifications").orderBy("sentAt", "desc").limit(50).get();
      renderHistory(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
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
        "<span>" + (item.type === "audio" ? "🔊 صوتي" : "💬 نصي") + " · " + formatDate(item.sentAt) + (item.local ? " · محلي" : "") + "</span>" +
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
    if (initFirebase()) {
      // Firestore may need an index for the orderBy queries used above.
      db.settings && db.settings({ ignoreUndefinedProperties: true });
    }
  });
})();
