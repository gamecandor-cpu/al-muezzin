package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Coordinates(val latitude: Double, val longitude: Double)

class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): Coordinates? {
        val fused = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { continuation ->
                val tokenSource = CancellationTokenSource()
                continuation.invokeOnCancellation { tokenSource.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            if (continuation.isActive) {
                                continuation.resume(Coordinates(location.latitude, location.longitude))
                            }
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last ->
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            last?.let { Coordinates(it.latitude, it.longitude) }
                                        )
                                    }
                                }
                                .addOnFailureListener {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }
        if (fused != null) return fused

        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            val best = providers.firstNotNullOfOrNull { provider ->
                runCatching { lm?.getLastKnownLocation(provider) }.getOrNull()
            }
            best?.let { Coordinates(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
