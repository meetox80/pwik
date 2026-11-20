package me.kotwicha.pwik.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.kotwicha.pwik.core.geo.LatLon

class LocationSource(private val context: Context) {

    private val manager get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun providers(lm: LocationManager): List<String> {
        val enabled = lm.getProviders(true)
        return listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 10_000): LatLon? {
        if (!hasPermission()) return null
        val lm = manager ?: return null
        val last = lastKnown(lm)
        if (last != null && System.currentTimeMillis() - last.time < 90_000) return LatLon(last.latitude, last.longitude)
        val providers = providers(lm)
        val fresh = if (providers.isEmpty()) null else withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListenerCompat {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }
                }
                val started = providers.count { provider ->
                    runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }.isSuccess
                }
                if (started == 0 && cont.isActive) cont.resume(null)
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
        val loc = fresh ?: last ?: return null
        return LatLon(loc.latitude, loc.longitude)
    }

    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 4_000): Flow<LatLon> = callbackFlow {
        val lm = manager
        val providers = lm?.let(::providers).orEmpty()
        if (!hasPermission() || lm == null || providers.isEmpty()) {
            close()
            return@callbackFlow
        }
        lastKnown(lm)?.let { trySend(LatLon(it.latitude, it.longitude)) }
        val listener = LocationListenerCompat { trySend(LatLon(it.latitude, it.longitude)) }
        providers.forEach { provider ->
            runCatching { lm.requestLocationUpdates(provider, intervalMs, 5f, listener, Looper.getMainLooper()) }
        }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }
}
