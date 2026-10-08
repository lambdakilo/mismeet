package app.mismeet.protocol

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** The plaintext of one entry, spec section 5.2. */
data class LocationPayload(
    val fixTime: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Int,
    val altitude: Int? = null,
    val speed: Double? = null,
    val course: Int? = null,
    val battery: Double? = null,
) {
    enum class Reason { NOT_AN_OBJECT, UNSUPPORTED_VERSION, INVALID }

    class DecodeException(val reason: Reason, val field: String? = null) :
        Exception(if (field == null) reason.name else "${reason.name} $field")

    fun encode(): String {
        val fields = mutableListOf(
            "\"v\":${ProtocolConstants.PAYLOAD_VERSION}",
            "\"ts\":$fixTime",
            "\"lat\":${JsonNumber.format(latitude, ProtocolConstants.COORDINATE_DECIMALS)}",
            "\"lon\":${JsonNumber.format(longitude, ProtocolConstants.COORDINATE_DECIMALS)}",
            "\"acc\":$accuracy",
        )
        altitude?.let { fields += "\"alt\":$it" }
        speed?.let { fields += "\"spd\":${JsonNumber.format(it, 1)}" }
        course?.let { fields += "\"hdg\":$it" }
        battery?.let { fields += "\"bat\":${JsonNumber.format(it, 2)}" }
        return fields.joinToString(",", "{", "}")
    }

    companion object {
        fun decode(json: String): LocationPayload {
            val element = try {
                Json.parseToJsonElement(json)
            } catch (e: Exception) {
                throw DecodeException(Reason.NOT_AN_OBJECT)
            }
            val fields = element as? JsonObject ?: throw DecodeException(Reason.NOT_AN_OBJECT)
            if (integer(fields["v"]) != ProtocolConstants.PAYLOAD_VERSION.toLong()) {
                throw DecodeException(Reason.UNSUPPORTED_VERSION)
            }
            val fixTime = integer(fields["ts"])?.takeIf { it >= 0 }
                ?: throw DecodeException(Reason.INVALID, "ts")
            val latitude = double(fields["lat"])?.takeIf { it in -90.0..90.0 }
                ?: throw DecodeException(Reason.INVALID, "lat")
            val longitude = double(fields["lon"])?.takeIf { it in -180.0..180.0 }
                ?: throw DecodeException(Reason.INVALID, "lon")
            val accuracy = integer(fields["acc"])?.takeIf { it in 0..Int.MAX_VALUE }
                ?: throw DecodeException(Reason.INVALID, "acc")
            return LocationPayload(
                fixTime = fixTime,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy.toInt(),
                altitude = integer(fields["alt"])?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt(),
                speed = double(fields["spd"])?.takeIf { it >= 0 },
                course = integer(fields["hdg"])?.takeIf { it in 0..359 }?.toInt(),
                battery = double(fields["bat"])?.takeIf { it in 0.0..1.0 },
            )
        }

        private fun double(element: JsonElement?): Double? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive.isString || primitive.booleanOrNull != null) return null
            return primitive.doubleOrNull?.takeIf { it.isFinite() }
        }

        private fun integer(element: JsonElement?): Long? {
            val value = double(element) ?: return null
            if (value != Math.rint(value) || abs(value) >= 9_007_199_254_740_992.0) return null
            return value.toLong()
        }
    }
}
