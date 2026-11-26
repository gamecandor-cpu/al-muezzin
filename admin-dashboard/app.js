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
      toast("تم إرسال الإشعار بنجاح");
      $("pushBody").value = "";
      $("audioFile").value = "";
      loadHistory();
    } catch (error) {
      $("sendStatus").textContent = "";
      toast("فشل الإرسال: " + (error && error.message ? error.message : error), true);
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
      toast("تعذّر تحميل الأجهزة: " + error.message, true);
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
      toast("تعذّر تحميل السجل: " + error.message, true);
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
        "<span>" + (item.type === "audio" ? "🔊 صوتي" : "💬 نصي") + " · " + formatDate(item.sentAt) + "</span>" +
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
