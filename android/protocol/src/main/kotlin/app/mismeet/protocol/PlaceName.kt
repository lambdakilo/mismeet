package app.mismeet.protocol

import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A grid cell of place names, spec section 8.7. */
data class PlaceCell(val row: Int, val col: Int) {
    fun bbox(): String =
        String.format(Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", row / 10.0, col / 5.0, (row + 1) / 10.0, (col + 1) / 5.0)

    companion object {
        fun of(point: LatLon) = PlaceCell(floor(point.latitude * 10).toInt(), floor(point.longitude * 5).toInt())

        /** The cells that the box from 5 km south-west to 5 km north-east of the position touches. */
        fun around(point: LatLon): List<PlaceCell> {
            val dLat = ProtocolConstants.PLACE_NAME_METRES / PlaceNames.METRES_PER_LAT
            val dLon = ProtocolConstants.PLACE_NAME_METRES / PlaceNames.metresPerLon(point.latitude)
            val low = of(LatLon(point.latitude - dLat, point.longitude - dLon))
            val high = of(LatLon(point.latitude + dLat, point.longitude + dLon))
            return (low.row..high.row).flatMap { row -> (low.col..high.col).map { PlaceCell(row, it) } }
        }
    }
}

data class Place(val id: Long, val place: String, val name: String, val point: LatLon)

object PlaceNames {
    const val METRES_PER_LAT = 110_574.0

    fun metresPerLon(latitude: Double) = cos(Math.toRadians(latitude)) * 111_320.0

    fun query(cell: PlaceCell): String =
        "[out:json][timeout:25];\nnode[\"place\"~\"^(suburb|town|village|city)$\"][\"name\"](${cell.bbox()});\nout;"

    fun parse(text: String): List<Place> =
        Json.parseToJsonElement(text).jsonObject["elements"]?.jsonArray.orEmpty().mapNotNull { element ->
            val node = element.jsonObject
            if (node["type"]?.jsonPrimitive?.content != "node") return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val lat = node["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = node["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val tags = node["tags"]?.jsonObject ?: return@mapNotNull null
            val place = tags["place"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = tags["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Place(id, place, name, LatLon(lat, lon))
        }

    /** The name of a position from the places of the cells around it, null when none is near. */
    fun name(point: LatLon, places: List<Place>): String? {
        val metresPerLon = metresPerLon(point.latitude)
        fun nearest(kinds: Set<String>): Place? = places.filter { it.place in kinds }
            .map { it to hypot((it.point.longitude - point.longitude) * metresPerLon, (it.point.latitude - point.latitude) * METRES_PER_LAT) }
            .filter { it.second <= ProtocolConstants.PLACE_NAME_METRES }
            .minWithOrNull(compareBy<Pair<Place, Double>> { it.second }.thenBy { it.first.id })?.first
        return (nearest(setOf("suburb", "town", "village")) ?: nearest(setOf("city")))?.name
    }
}
