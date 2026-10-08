package app.mismeet.protocol

import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** One position report from the platform, with the platform's unknown markers already removed. */
@Serializable
data class LocationFix(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Double,
    val ellipsoidalAltitude: Double? = null,
    val speed: Double? = null,
    val course: Double? = null,
) {
    fun payload(battery: Double?): LocationPayload = LocationPayload(
        fixTime = max(0L, timeMillis / 1000),
        latitude = latitude,
        longitude = longitude,
        accuracy = horizontalAccuracy.roundToInt(),
        altitude = ellipsoidalAltitude?.roundToInt(),
        speed = speed,
        course = course?.let { it.roundToInt() % 360 },
        battery = battery,
    )
}
