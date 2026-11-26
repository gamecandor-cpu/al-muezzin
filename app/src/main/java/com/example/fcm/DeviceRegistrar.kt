package com.example.fcm

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Registers the FCM device token in the project's Firebase Realtime Database
 * so the admin dashboard can list active devices and target them with text or
 * audio pushes.
 *
 * Uses the public REST endpoint `https://<project>.firebaseio.com/...` so it
 * works on the free (Spark) plan \u2014 no Cloud Functions or Firestore required.
 * The dashboard authenticates with Firebase Auth, RTDB rule:
 *   devices.$id: read = auth != null, write = open
 */
object DeviceRegistrar {

    private const val TAG = "DeviceRegistrar"
    private const val PREFS = "muezzin_push_prefs"
    private const val KEY_LAST_TOKEN = "last_registered_token"
    private const val KEY_DEVICE_ID = "device_id"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Stable, anonymous device id derived from ANDROID_ID \u2014 regenerates on
     * factory reset / app reinstall. Used as the RTDB document key.
     */
    fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val raw = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (e: Exception) {
            ""
        }
        val id = if (raw.isNotBlank()) "dev_$raw" else "dev_fallback_${System.currentTimeMillis()}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun register(context: Context, token: String) {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()
        if (projectId.isBlank()) {
            Log.i(TAG, "FIREBASE_PROJECT_ID not configured \u2014 device not registered.")
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_TOKEN, null) == token) {
            // Token unchanged but we keep lastSeenAt fresh so the dashboard
            // shows the device as recently active.
            writeLastSeen(projectId, deviceId(context), prefs)
            return
        }

        scope.launch {
            try {
                val id = deviceId(context)
                val payload = JSONObject().apply {
                    put("token", token)
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("platform", "android")
                    put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    put("appId", BuildConfig.FIREBASE_APP_ID)
                    put("lastSeenAt", System.currentTimeMillis())
                }
                val url = "https://$projectId-default-rtdb.firebaseio.com/devices/$id.json"
                val request = Request.Builder()
                    .url(url)
                    .put(payload.toString().toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "Device registration RTDB update HTTP ${response.code}")
                }
                prefs.edit().putString(KEY_LAST_TOKEN, token).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed", e)
            }
        }
    }

    private fun writeLastSeen(projectId: String, id: String, prefs: android.content.SharedPreferences) {
        scope.launch {
            try {
                val body = JSONObject().apply { put("lastSeenAt", System.currentTimeMillis()) }
                val url = "https://$projectId-default-rtdb.firebaseio.com/devices/$id/lastSeenAt.json"
                val request = Request.Builder()
                    .url(url)
                    .put(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    Log.d(TAG, "Heartbeat HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Heartbeat failed (offline?): ${e.message}")
            }
        }
    }
}
