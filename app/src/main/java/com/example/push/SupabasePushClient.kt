package com.example.push

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Supabase-backed push transport for the Al-Muezzin app. No Firebase at all.
 *
 *  - Registration: the app upserts a row into `devices` over the REST API.
 *  - Live push:    a WebSocket to Supabase Realtime receives every INSERT into
 *                  `notifications` the moment the dashboard writes one.
 *  - Catch-up:     on start (and periodically) the app pulls the notifications
 *                  it may have missed while it was offline.
 *
 * Everything runs against the public anon key; Row Level Security decides what
 * each role may do. The listeners are wired through [PushListener].
 */
object SupabasePushClient {

    private const val TAG = "SupabasePush"
    private const val PREFS = "muezzin_supabase_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_NOTIF_ID = "last_notif_id"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20L, TimeUnit.SECONDS)
        .readTimeout(0L, TimeUnit.MILLISECONDS)   // Realtime is long-lived
        .pingInterval(30L, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var running = false

    private fun baseUrl() = BuildConfigHolder.supabaseUrl.trimEnd('/')
    private fun anonKey() = BuildConfigHolder.supabaseAnonKey.trim()

    val isConfigured: Boolean
        get() = baseUrl().isNotBlank() && anonKey().isNotBlank()

    // ------------------------------------------------------------- identity
    fun deviceId(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")
        val id = if (raw.isNotBlank()) "dev_$raw" else "dev_${System.currentTimeMillis()}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ----------------------------------------------------------- registration
    /** Upserts this device so the dashboard can list and target it. */
    fun register(context: Context) {
        if (!isConfigured) return
        scope.launch {
            try {
                val id = deviceId(context)
                val fcmToken = currentFcmToken(context)
                val payload = JSONObject().apply {
                    put("id", id)
                    put("platform", "android")
                    put("app_version", BuildConfigHolder.versionName)
                    put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    put("app_id", "almuezzin")
                    put("last_seen_at", java.time.Instant.now().toString())
                    if (!fcmToken.isNullOrBlank()) put("fcm_token", fcmToken)
                }
                val req = Request.Builder()
                    .url("${baseUrl()}/rest/v1/devices?on_conflict=id")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer ${anonKey()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(payload.toString().toRequestBody(json))
                    .build()
                client.newCall(req).execute().use { resp ->
                    Log.i(TAG, "device registration HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "device registration failed: ${e.message}")
            }
        }
    }

    /** Touches last_seen_at without re-registering. */
    fun heartbeat(context: Context) {
        if (!isConfigured) return
        scope.launch {
            runCatching {
                val body = JSONObject().put("last_seen_at", java.time.Instant.now().toString())
                val req = Request.Builder()
                    .url("${baseUrl()}/rest/v1/devices?id=eq.${deviceId(context)}")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer ${anonKey()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(body.toString().toRequestBody(json))
                    .build()
                client.newCall(req).execute().use { it.code }
            }
        }
    }

    // ------------------------------------------------------------- fcm token
    /** Cached FCM token, if Firebase Messaging has produced one yet. */
    private fun currentFcmToken(context: Context): String? {
        prefs(context).getString(KEY_FCM_TOKEN, null)?.let { return it }
        return runCatching { FirebaseMessaging.getInstance().token.result }.getOrNull()
    }

    /**
     * Stores the FCM token and pushes it onto this device's row so the
     * dashboard can address a push to it even when the app is closed.
     */
    fun registerFcmToken(context: Context, token: String) {
        if (!isConfigured || token.isBlank()) return
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
        scope.launch {
            runCatching {
                val body = JSONObject().put("fcm_token", token).put("last_seen_at", java.time.Instant.now().toString())
                val req = Request.Builder()
                    .url("${baseUrl()}/rest/v1/devices?id=eq.${deviceId(context)}")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer ${anonKey()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(body.toString().toRequestBody(json))
                    .build()
                client.newCall(req).execute().use { resp ->
                    Log.i(TAG, "fcm token saved HTTP ${resp.code}")
                    // If the row did not exist yet, create it.
                    if (resp.code == 404) register(context)
                }
            }
        }
    }

    /** Fetches the current FCM token (registering with FCM if needed) and saves it. */
    fun refreshFcmToken(context: Context) {
        if (!isConfigured) return
        scope.launch {
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    registerFcmToken(context, token)
                }
            }
        }
    }

    // ---------------------------------------------------------- live delivery
    /** Opens a Realtime WebSocket and dispatches every new notification. */
    fun startListening(context: Context, listener: PushListener) {
        if (!isConfigured || running) return
        running = true
        scope.launch {
            // First, catch up on anything missed offline.
            fetchMissed(context, listener)
            // Then hold a live connection for instant delivery.
            openRealtime(context, listener)
        }
    }

    fun stopListening() {
        running = false
        webSocket?.close(1000, "stop")
        webSocket = null
    }

    private fun openRealtime(context: Context, listener: PushListener) {
        val ref = java.util.UUID.randomUUID().toString()
        val url = "${baseUrl().replace("https", "wss")}/realtime/v1/websocket" +
            "?apikey=${anonKey()}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                Log.i(TAG, "realtime connected")
                socket.send(JSONObject().apply {
                    put("topic", "realtime:notifications")
                    put("event", "phoenix_join")
                    put("ref", ref)
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("private", false)
                            put("postgres_changes", JSONArray().put(JSONObject().apply {
                                put("event", "INSERT")
                                put("schema", "public")
                                put("table", "notifications")
                            }))
                        })
                    })
                }.toString())
                heartbeatLoop(socket, ref)
            }

            override fun onMessage(socket: WebSocket, text: String) {
                handleMessage(text, listener)
            }

            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "realtime failure: ${t.message}")
                webSocket = null
                if (running) {
                    scope.launch {
                        delay(5000)
                        if (running) openRealtime(context, listener)
                    }
                }
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                webSocket = null
            }
        })
    }

    private fun heartbeatLoop(socket: WebSocket, ref: String) {
        scope.launch {
            var n = 1
            while (isActive && running) {
                delay(30_000)
                if (!socket.send(JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("ref", "${ref}_${n++}")
                        put("payload", JSONObject())
                    }.toString())) break
            }
        }
    }

    private fun handleMessage(text: String, listener: PushListener) {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (msg.optString("event") != "postgres_changes") return
        val data = msg.optJSONObject("payload")?.optJSONObject("data") ?: return
        if (data.optString("event_type") != "INSERT") return
        // Supabase Realtime puts the inserted row under "record" (older builds used
        // "new"); accept either so live delivery never silently drops.
        val record = data.optJSONObject("record") ?: data.optJSONObject("new") ?: return
        dispatch(record, listener)
    }

    private fun dispatch(record: JSONObject, listener: PushListener) {
        val id = record.optLong("id", 0L)
        val type = record.optString("type", "text")
        val title = record.optString("title", "تنبيه")
        val body = record.optString("body", "")
        val audioUrl = record.optString("audio_url", "").ifBlank { null }
        scope.launch {
            runCatching {
                listener.onNotification(
                    PushMessage(id, type, title, body, audioUrl)
                )
            }
        }
    }

    // ------------------------------------------------------------- catch-up
    /** Pulls notifications newer than the last one we saw. */
    fun fetchMissed(context: Context, listener: PushListener) {
        if (!isConfigured) return
        scope.launch {
            try {
                val prefs = prefs(context)
                val lastId = prefs.getLong(KEY_LAST_NOTIF_ID, 0L)

                // Fresh install / first run: do NOT replay old notifications. Mark
                // everything already in the table as seen and show nothing — only
                // notifications sent from now on should reach the user.
                if (lastId == 0L) {
                    val peek = Request.Builder()
                        .url("${baseUrl()}/rest/v1/notifications?select=id&order=id.desc&limit=1")
                        .addHeader("apikey", anonKey())
                        .addHeader("Authorization", "Bearer ${anonKey()}")
                        .build()
                    client.newCall(peek).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "[]")
                            val maxId = arr.optJSONObject(0)?.optLong("id", 0L) ?: 0L
                            if (maxId > 0) prefs.edit().putLong(KEY_LAST_NOTIF_ID, maxId).apply()
                        }
                    }
                    return@launch // never replay history on a new install
                }

                val filter = "?id=gt.$lastId&order=id.desc&limit=50"
                val req = Request.Builder()
                    .url("${baseUrl()}/rest/v1/notifications$filter")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer ${anonKey()}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val arr = JSONArray(resp.body?.string() ?: "[]")
                    var maxId = lastId
                    val pending = ArrayList<JSONObject>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optLong("id", 0L)
                        if (id > maxId) maxId = id
                        pending.add(o)
                    }
                    // Deliver oldest first so ordering is natural.
                    for (i in pending.size - 1 downTo 0) {
                        dispatch(pending[i], listener)
                    }
                    if (maxId > lastId) {
                        prefs.edit().putLong(KEY_LAST_NOTIF_ID, maxId).apply()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "catch-up failed: ${e.message}")
            }
        }
    }
}

data class PushMessage(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val audioUrl: String?
)

fun interface PushListener {
    fun onNotification(message: PushMessage)
}