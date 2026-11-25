package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class UserPushProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fcmToken: String? = null,
    val cityName: String? = null,
    val athanEnabled: Boolean = true,
    val athkarAutoEnabled: Boolean = true,
    val subscribedTopics: List<String> = emptyList(),
    val qadaRemaining: Long = 0L,
    val qadaCompleted: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BroadcastReminder(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val title: String = "",
    val body: String = "",
    val reminderKind: String = "text", // "text" | "audio"
    val persistenceMode: String = "pinned", // "pinned" | "temporary"
    val deliveryMode: String = "now", // "now" | "scheduled" | "daily"
    val scheduledTimeHHmm: String = "08:00",
    val scheduledTimes: List<String> = emptyList(),
    val audioUrl: String? = null,
    val normalizedAudioUrl: String? = null,
    val audioSourceType: String? = null,
    @field:JvmField val isActive: Boolean = true,
    val triggerNonce: Long = 1L,
    val expiresAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isAudio: Boolean
        get() = reminderKind == "audio" && !effectiveAudioUrl.isNullOrBlank()

    val isPinned: Boolean
        get() = persistenceMode == "pinned"

    val isDaily: Boolean
        get() = deliveryMode == "daily"

    val allScheduledTimes: List<String>
        get() = (if (scheduledTimes.isNotEmpty()) scheduledTimes else listOf(scheduledTimeHHmm))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("08:00") }

    val effectiveAudioUrl: String?
        get() = normalizedAudioUrl?.takeIf { it.isNotBlank() } ?: audioUrl?.takeIf { it.isNotBlank() }

    fun isNotExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAtMillis ?: return true
        if (exp <= 0L) return true
        return nowMillis <= exp
    }
}

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserPushProfile?> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserPushProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveBroadcastReminders(): Flow<List<BroadcastReminder>> {
        val path = "broadcast_reminders"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                val now = System.currentTimeMillis()
                snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(
                            BroadcastReminder::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .filter { it.isActive && it.isNotExpired(now) }
                    .sortedWith(
                        compareByDescending<BroadcastReminder> { it.isPinned }
                            .thenByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                    )
            }
            .retryWhen { cause, attempt ->
                val firestoreError = generateSequence(cause) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && cause is CancellationException) throw cause
                val exceptionToLog = firestoreError ?: (cause as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                val backoffMs = minOf(30_000L, 2_000L * (1L shl attempt.toInt().coerceAtMost(4)))
                delay(backoffMs)
                true
            }
    }

    suspend fun fetchActiveBroadcastRemindersOnce(): Result<List<BroadcastReminder>> {
        val path = "broadcast_reminders"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            val now = System.currentTimeMillis()
            val items = snapshot.documents
                .mapNotNull { doc ->
                    doc.toObject(
                        BroadcastReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .filter { it.isActive && it.isNotExpired(now) }
                .sortedWith(
                    compareByDescending<BroadcastReminder> { it.isPinned }
                        .thenByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                )
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun saveOrUpdateProfile(
        fcmToken: String?,
        cityName: String?,
        athanEnabled: Boolean,
        athkarAutoEnabled: Boolean,
        qadaRemaining: Int,
        qadaCompleted: Int
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            val safeRemaining = qadaRemaining.coerceIn(0, 5000).toLong()
            val safeCompleted = qadaCompleted.coerceIn(0, 5000).toLong()
            val safeToken = fcmToken?.takeIf { it.isNotBlank() }?.take(512)
            val safeCity = cityName?.takeIf { it.isNotBlank() }?.take(120)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mutableMapOf<String, Any>(
                    "athanEnabled" to athanEnabled,
                    "athkarAutoEnabled" to athkarAutoEnabled,
                    "subscribedTopics" to DEFAULT_FCM_TOPICS,
                    "qadaRemaining" to safeRemaining,
                    "qadaCompleted" to safeCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeToken != null) updatePayload["fcmToken"] = safeToken
                if (safeCity != null) updatePayload["cityName"] = safeCity
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "athanEnabled" to athanEnabled,
                    "athkarAutoEnabled" to athkarAutoEnabled,
                    "subscribedTopics" to DEFAULT_FCM_TOPICS,
                    "qadaRemaining" to safeRemaining,
                    "qadaCompleted" to safeCompleted,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeToken != null) createPayload["fcmToken"] = safeToken
                if (safeCity != null) createPayload["cityName"] = safeCity
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CloudSyncRepository"
        private const val PREFS_FCM = "muezzin_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        val DEFAULT_FCM_TOPICS = listOf(
            "broadcast_reminders",
            "muezzin_announcements",
            "religious_occasions",
            "athkar_reminders"
        )

        fun authStateFlow(auth: FirebaseAuth = Firebase.auth): Flow<FirebaseUser?> = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

        fun saveLocalFcmToken(context: Context, token: String) {
            context.applicationContext
                .getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }

        fun getLocalFcmToken(context: Context): String? {
            return context.applicationContext
                .getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }

        suspend fun initializeFcmAndSubscribe(context: Context): String? {
            return runCatching {
                val messaging = Firebase.messaging
                DEFAULT_FCM_TOPICS.forEach { topic ->
                    runCatching { messaging.subscribeToTopic(topic).await() }
                }
                val token: String? = messaging.token.await()
                if (!token.isNullOrBlank()) {
                    saveLocalFcmToken(context, token)
                    syncFcmTokenIfSignedIn(context, token)
                }
                token
            }.onFailure { e ->
                Log.w(TAG, "FCM token/topic initialization deferred: ${e.message}")
            }.getOrNull()
        }

        suspend fun syncFcmTokenIfSignedIn(context: Context, token: String) {
            val uid = Firebase.auth.currentUser?.uid ?: return
            val safeToken = token.takeIf { it.isNotBlank() }?.take(512) ?: return
            runCatching {
                val db = FirebaseFirestore.getInstance(
                    context.applicationContext.getString(R.string.firestore_database_id)
                )
                val docRef = db.collection("users").document(uid)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "fcmToken" to safeToken,
                            "subscribedTopics" to DEFAULT_FCM_TOPICS,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }.onFailure { e ->
                Log.w(TAG, "Deferred FCM token Firestore sync: ${e.message}")
            }
        }

        fun attemptAutoSignIn(
            context: Context,
            credentialManager: CredentialManager,
            scope: CoroutineScope,
            onAuthSuccess: () -> Unit,
            onUnauthenticated: () -> Unit
        ) {
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
                return
            }
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                onUnauthenticated()
                return
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            scope.launch {
                try {
                    val cred = credentialManager.getCredential(context, request).credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                        onAuthSuccess()
                    } else {
                        onUnauthenticated()
                    }
                } catch (_: Exception) {
                    onUnauthenticated()
                }
            }
        }

        fun onGoogleSignInClicked(
            context: Context,
            credentialManager: CredentialManager,
            scope: CoroutineScope,
            onAuthSuccess: () -> Unit,
            onAuthError: (String) -> Unit,
            onAuthCancelled: () -> Unit = {}
        ) {
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                onAuthError("إعدادات Google Sign-In غير متوفرة")
                return
            }
            val activity = context as? Activity
            if (activity == null) {
                onAuthError("تعذر فتح نافذة تسجيل الدخول")
                return
            }
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            scope.launch {
                try {
                    val cred = credentialManager.getCredential(activity, request).credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                        onAuthSuccess()
                    } else {
                        onAuthError("نوع بيانات الاعتماد غير متوقع")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                    onAuthCancelled()
                } catch (e: Exception) {
                    Log.e("Auth", "Google Sign-In failed", e)
                    onAuthError(e.localizedMessage ?: "فشل تسجيل الدخول بحساب Google")
                }
            }
        }

        fun signOut(
            credentialManager: CredentialManager,
            scope: CoroutineScope,
            onSignOutComplete: () -> Unit = {}
        ) {
            scope.launch {
                withContext(NonCancellable) {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        Log.w("Auth", "Failed to clear credential state: ${e.message}")
                    } finally {
                        Firebase.auth.signOut()
                        onSignOutComplete()
                    }
                }
            }
        }
    }
}
