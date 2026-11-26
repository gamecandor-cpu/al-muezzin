package com.example.fcm

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Firebase Cloud Messaging wiring for the Al-Muezzin app.
 *
 * The Firebase web configuration is injected at build time through the Secrets
 * Gradle plugin (see `.env` / `.env.example`) so that no `google-services.json`
 * has to live in the repository. When the values are missing the app degrades
 * gracefully: OneSignal keeps working and FCM is simply skipped.
 */
object FirebasePushConfig {

    private const val TAG = "FirebasePushConfig"

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank()

    /** Backend that receives device registrations (Cloud Function). */
    val registrationUrl: String
        get() = BuildConfig.PUSH_BACKEND_URL.trim()

    /** Initializes Firebase once per process. Safe to call repeatedly. */
    fun initialize(context: Context): Boolean {
        if (!isConfigured) {
            Log.i(TAG, "Firebase is not configured (missing FIREBASE_* values) — FCM disabled.")
            return false
        }
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return try {
            val options = FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID.trim())
                .setApplicationId(BuildConfig.FIREBASE_APP_ID.trim())
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID.trim())
                .setApiKey(BuildConfig.FIREBASE_API_KEY.trim())
                .build()
            FirebaseApp.initializeApp(context, options)
            Log.i(TAG, "Firebase initialized for project ${BuildConfig.FIREBASE_PROJECT_ID}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
            false
        }
    }

    /** Requests the FCM registration token and hands it to the backend. */
    fun requestTokenAndRegister(context: Context) {
        if (!isConfigured) return
        try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.token.addOnSuccessListener { token ->
                Log.i(TAG, "FCM token acquired")
                DeviceRegistrar.register(context, token)
                messaging.subscribeToTopic(TOPIC_ALL).addOnSuccessListener {
                    Log.i(TAG, "Subscribed to topic $TOPIC_ALL")
                }.addOnFailureListener { error ->
                    Log.w(TAG, "Topic subscription failed", error)
                }
            }.addOnFailureListener { error ->
                Log.w(TAG, "Failed to acquire FCM token", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to request FCM token", e)
        }
    }

    /** Broadcast topic every installed app subscribes to. */
    const val TOPIC_ALL = "all"
}
