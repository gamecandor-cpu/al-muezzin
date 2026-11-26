package com.example.push

import com.example.BuildConfig

/**
 * Thin wrapper over the values the Secrets Gradle plugin injects from `.env`.
 * Keeps [SupabasePushClient] free of a direct BuildConfig dependency so it is
 * easier to test.
 */
object BuildConfigHolder {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY

    val versionName: String
        get() = BuildConfig.VERSION_NAME
}