package com.example.fcm

import android.content.Context
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
 * Sends the FCM device token to the Cloud Function that stores it in Firestore,
 * so the admin dashboard can target this device with text or audio pushes.
 */
object DeviceRegistrar {

    private const val TAG = "DeviceRegistrar"
    private const val PREFS = "muezzin_push_prefs"
    private const val KEY_LAST_TOKEN = "last_registered_token"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(context: Context, token: String) {
        val endpoint = FirebasePushConfig.registrationUrl
        if (endpoint.isBlank()) {
            Log.i(TAG, "PUSH_BACKEND_URL is not configured — device not registered.")
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Avoid a network round trip on every app start.
        if (prefs.getString(KEY_LAST_TOKEN, null) == token) {
            return
        }

        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("token", token)
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("platform", "android")
                }
                val request = Request.Builder()
                    .url(endpoint.trimEnd('/') + "/registerDevice")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "Device registration HTTP ${response.code}")
                }
                prefs.edit()
                    .putString(KEY_LAST_TOKEN, token)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed", e)
            }
        }
    }
}
