package app.mismeet.android.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.PowerManager
import app.mismeet.protocol.LocationFix
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Fixes from the platform location manager, never from a fused provider. */
object LocationProvider {
    private const val FIX_TIMEOUT_MILLIS = 45_000L

    fun hasForegroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Stock Android 12 stops a foreground service it considers wasteful unless the app is exempted. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context): LocationFix? {
        if (!hasForegroundPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } ?: return null
        val location = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                }
            }
        } ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        return location?.let(::toFix)
    }

    fun toFix(location: Location): LocationFix? {
        if (!location.hasAccuracy()) return null
        return LocationFix(
            timeMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracy = location.accuracy.toDouble(),
            ellipsoidalAltitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            course = if (location.hasBearing()) location.bearing.toDouble() else null,
        )
    }
}
