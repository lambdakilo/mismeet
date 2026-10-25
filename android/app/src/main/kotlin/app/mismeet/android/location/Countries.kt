package app.mismeet.android.location

import app.mismeet.protocol.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Which country a point lies in, from the Natural Earth borders that scripts/make-countries.py
 * writes into the app's assets, so no position leaves the phone. Borders are simplified to about
 * 2 km, so a point that close to one may land in the neighbour, and a point off the simplified
 * coast, such as central Stockholm, takes the nearest country within [COAST_DEGREES].
 */
class Countries(json: String) {
    private class Polygon(val code: String, val rings: List<DoubleArray>) {
        private val outer = rings.first()
        private val minLon = outer.filterIndexed { i, _ -> i % 2 == 0 }.min()
        private val maxLon = outer.filterIndexed { i, _ -> i % 2 == 0 }.max()
        private val minLat = outer.filterIndexed { i, _ -> i % 2 == 1 }.min()
        private val maxLat = outer.filterIndexed { i, _ -> i % 2 == 1 }.max()

        /** Even-odd ray casting over every ring, so a hole such as Lesotho inside South Africa is outside. */
        fun contains(point: LatLon): Boolean {
            if (point.latitude !in minLat..maxLat || point.longitude !in minLon..maxLon) return false
            var inside = false
            for (ring in rings) {
                var j = ring.size - 2
                for (i in ring.indices step 2) {
                    val (x1, y1, x2, y2) = listOf(ring[i], ring[i + 1], ring[j], ring[j + 1])
                    if ((y1 > point.latitude) != (y2 > point.latitude) &&
                        point.longitude < (x2 - x1) * (point.latitude - y1) / (y2 - y1) + x1
                    ) {
                        inside = !inside
                    }
                    j = i
                }
            }
            return inside
        }

        /** Distance to the outer border in degrees of latitude, or null when it is beyond [COAST_DEGREES]. */
        fun coastDistance(point: LatLon): Double? {
            val scale = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
            val lonLimit = COAST_DEGREES / scale
            if (point.latitude !in minLat - COAST_DEGREES..maxLat + COAST_DEGREES ||
                point.longitude !in minLon - lonLimit..maxLon + lonLimit
            ) {
                return null
            }
            var best = Double.MAX_VALUE
            for (i in 0 until outer.size - 2 step 2) {
                val ax = (outer[i] - point.longitude) * scale
                val ay = outer[i + 1] - point.latitude
                val bx = (outer[i + 2] - point.longitude) * scale
                val by = outer[i + 3] - point.latitude
                val length = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
                val t = if (length == 0.0) 0.0 else (-(ax * (bx - ax) + ay * (by - ay)) / length).coerceIn(0.0, 1.0)
                best = min(best, hypot(ax + t * (bx - ax), ay + t * (by - ay)))
            }
            return best.takeIf { it <= COAST_DEGREES }
        }
    }

    private val polygons = Json.parseToJsonElement(json).jsonObject.flatMap { (code, country) ->
        country.jsonArray.map { polygon ->
            Polygon(code, polygon.jsonArray.map { ring -> ring.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray() })
        }
    }

    /** The ISO 3166-1 alpha-2 code, or Natural Earth's own three letters for a place without one. */
    fun code(point: LatLon): String? =
        polygons.firstOrNull { it.contains(point) }?.code
            ?: polygons.mapNotNull { polygon -> polygon.coastDistance(point)?.let { polygon to it } }.minByOrNull { it.second }?.first?.code

    /**
     * The country the map opens on: the one this phone is in, or else the one holding the most
     * contacts, the first of them on a tie.
     */
    fun home(own: LatLon?, contacts: List<LatLon>): String? =
        own?.let(::code) ?: contacts.mapNotNull(::code).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    companion object {
        /** About 20 km. */
        const val COAST_DEGREES = 0.2
    }
}
