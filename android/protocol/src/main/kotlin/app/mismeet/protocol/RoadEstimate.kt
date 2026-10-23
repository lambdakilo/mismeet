package app.mismeet.protocol

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class LatLon(val latitude: Double, val longitude: Double)

enum class TravelMode {
    FOOT, BIKE, CAR;

    companion object {
        fun of(speed: Double): TravelMode = when {
            speed >= ProtocolConstants.CAR_SPEED_FROM -> CAR
            speed >= ProtocolConstants.BIKE_SPEED_FROM -> BIKE
            else -> FOOT
        }
    }
}

/** A grid cell of road data, spec section 8.6. */
data class RoadCell(val row: Int, val col: Int) {
    fun bbox(): String =
        String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", row / 100.0, col / 50.0, (row + 1) / 100.0, (col + 1) / 50.0)

    companion object {
        fun of(point: LatLon) = RoadCell(floor(point.latitude * 100).toInt(), floor(point.longitude * 50).toInt())
    }
}

data class RoadWay(val id: Long, val tags: Map<String, String>, val points: List<LatLon>) {
    fun allows(mode: TravelMode, forward: Boolean): Boolean {
        val highway = tags["highway"] ?: return false
        return when (mode) {
            TravelMode.CAR -> highway in CAR_WAYS && when {
                tags["oneway"] == "-1" -> !forward
                tags["oneway"] in setOf("yes", "true", "1") || tags["junction"] in setOf("roundabout", "circular") -> forward
                else -> true
            }
            TravelMode.BIKE -> tags["bicycle"] != "no" &&
                (highway in BIKE_WAYS || highway in setOf("footway", "pedestrian") && tags["bicycle"] in setOf("yes", "designated"))
            TravelMode.FOOT -> tags["foot"] != "no" && highway in FOOT_WAYS
        }
    }

    private companion object {
        val MAIN_ROADS = listOf("primary", "secondary", "tertiary").flatMap { listOf(it, "${it}_link") }
        val CAR_WAYS = setOf("motorway", "motorway_link", "trunk", "trunk_link", "unclassified", "residential", "living_street", "road") + MAIN_ROADS
        val BIKE_WAYS = setOf("cycleway", "path", "track", "living_street", "residential", "unclassified", "road") + MAIN_ROADS
        val FOOT_WAYS = setOf("footway", "pedestrian", "path", "track", "cycleway", "living_street", "residential", "unclassified", "road") + MAIN_ROADS
    }
}

object Overpass {
    const val URL = "https://overpass-api.de/api/interpreter"
    private const val HIGHWAYS = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|" +
        "tertiary|tertiary_link|unclassified|residential|living_street|road|cycleway|path|track|footway|pedestrian"
    private val KEPT_TAGS = setOf("highway", "oneway", "junction", "bicycle", "foot")

    fun query(cell: RoadCell): String =
        "[out:json][timeout:25];\nway[\"highway\"~\"^($HIGHWAYS)$\"](${cell.bbox()});\nout tags geom;"

    fun parse(text: String): List<RoadWay> =
        Json.parseToJsonElement(text).jsonObject["elements"]?.jsonArray.orEmpty().mapNotNull { element ->
            val way = element.jsonObject
            if (way["type"]?.jsonPrimitive?.content != "way") return@mapNotNull null
            val id = way["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val tags = way["tags"]?.jsonObject.orEmpty().filterKeys { it in KEPT_TAGS }.mapValues { it.value.jsonPrimitive.content }
            val points = way["geometry"]?.jsonArray.orEmpty().mapNotNull { point ->
                val lat = point.jsonObject["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = point.jsonObject["lon"]?.jsonPrimitive?.doubleOrNull
                if (lat != null && lon != null) LatLon(lat, lon) else null
            }
            if (points.size < 2) null else RoadWay(id, tags, points)
        }
}

/** The ways of the loaded cells, joined where they share a coordinate. */
class RoadNetwork(ways: Collection<RoadWay>) {
    internal data class Node(val lat: Long, val lon: Long)

    internal class Segment(val way: RoadWay, val from: LatLon, val to: LatLon, val forward: Boolean) {
        val fromNode = node(from)
        val toNode = node(to)
    }

    internal val segments: List<Segment>
    internal val leaving: Map<Node, List<Segment>>

    init {
        segments = ways.distinctBy { it.id }.flatMap { way ->
            way.points.zipWithNext().filter { (a, b) -> a != b }.flatMap { (a, b) ->
                listOf(Segment(way, a, b, true), Segment(way, b, a, false))
            }
        }
        leaving = segments.groupBy { it.fromNode }
    }

    internal companion object {
        fun node(point: LatLon) = Node((point.latitude * 1e7).roundToLong(), (point.longitude * 1e7).roundToLong())
    }
}

sealed interface EstimateResult {
    data class Estimate(val mode: TravelMode, val path: List<LatLon>, val point: LatLon) : EstimateResult
    data class NeedsCell(val cell: RoadCell) : EstimateResult
    data object None : EstimateResult
}

/**
 * Spec section 8.6. A cell that is neither loaded nor unavailable comes back as [EstimateResult.NeedsCell];
 * the caller loads it and asks again, and counts the loads against the limit per estimate.
 */
object RoadEstimator {
    fun estimate(
        fix: LatLon,
        speed: Double?,
        heading: Int?,
        ageSeconds: Long,
        network: RoadNetwork,
        loaded: Set<RoadCell>,
        unavailable: Set<RoadCell> = emptySet(),
    ): EstimateResult {
        if (speed == null || heading == null || speed < ProtocolConstants.ESTIMATE_MIN_SPEED) return EstimateResult.None
        if (ageSeconds > ProtocolConstants.ESTIMATE_MAX_FIX_AGE_SECONDS) return EstimateResult.None
        val length = speed * min(ageSeconds.coerceAtLeast(0), ProtocolConstants.ESTIMATE_SPAN_SECONDS)
        if (length <= 0) return EstimateResult.None
        val mode = TravelMode.of(speed)
        val plane = Plane(fix)
        startCells(plane).firstOrNull { it !in loaded && it !in unavailable }?.let { return EstimateResult.NeedsCell(it) }

        val start = network.segments
            .filter { it.way.allows(mode, it.forward) && angle(plane.bearing(it.from, it.to), heading.toDouble()) <= ProtocolConstants.ROAD_SNAP_BEARING_DEGREES }
            .map { it to plane.closest(it.from, it.to) }
            .filter { (_, snap) -> hypot(snap.first, snap.second) <= ProtocolConstants.ROAD_SNAP_METRES }
            .minWithOrNull(
                compareBy<Pair<RoadNetwork.Segment, Pair<Double, Double>>> { (_, snap) -> hypot(snap.first, snap.second) }
                    .thenBy { (segment, _) -> angle(plane.bearing(segment.from, segment.to), heading.toDouble()) }
                    .thenBy { (segment, _) -> segment.way.id },
            ) ?: return EstimateResult.None

        var segment = start.first
        var position = start.second
        var remaining = length
        val path = mutableListOf(plane.point(position))
        repeat(MAX_STEPS) {
            val end = plane.xy(segment.to)
            val left = hypot(end.first - position.first, end.second - position.second)
            if (remaining <= left) {
                val t = remaining / left
                val stop = plane.point(position.first + (end.first - position.first) * t, position.second + (end.second - position.second) * t)
                return EstimateResult.Estimate(mode, path + stop, stop)
            }
            remaining -= left
            position = end
            path += segment.to
            val cell = RoadCell.of(segment.to)
            if (cell !in loaded) {
                return if (cell in unavailable) EstimateResult.Estimate(mode, path, segment.to) else EstimateResult.NeedsCell(cell)
            }
            val bearing = plane.bearing(segment.from, segment.to)
            val current = segment
            segment = network.leaving[current.toNode].orEmpty()
                .filter { it.way.allows(mode, it.forward) && it.toNode != current.fromNode }
                .minWithOrNull(
                    compareBy<RoadNetwork.Segment> { angle(plane.bearing(it.from, it.to), bearing) }
                        .thenBy { if (it.way.id == current.way.id) 0 else 1 }
                        .thenBy { it.way.id },
                ) ?: return EstimateResult.Estimate(mode, path, current.to)
        }
        return EstimateResult.Estimate(mode, path, path.last())
    }

    /** The cells of the fix and of the points one snap distance north, south, east and west of it. */
    fun startCells(fix: LatLon): List<RoadCell> = startCells(Plane(fix))

    private fun startCells(plane: Plane): List<RoadCell> {
        val d = ProtocolConstants.ROAD_SNAP_METRES
        return listOf(0.0 to 0.0, 0.0 to d, 0.0 to -d, d to 0.0, -d to 0.0)
            .map { (x, y) -> RoadCell.of(plane.point(x, y)) }
            .distinct()
    }

    private fun angle(a: Double, b: Double): Double = abs((a - b + 540.0) % 360.0 - 180.0)

    private const val MAX_STEPS = 10_000

    private class Plane(private val origin: LatLon) {
        private val metresPerLon = cos(Math.toRadians(origin.latitude)) * 111_320.0

        fun xy(point: LatLon) = (point.longitude - origin.longitude) * metresPerLon to (point.latitude - origin.latitude) * METRES_PER_LAT

        fun point(x: Double, y: Double) = LatLon(origin.latitude + y / METRES_PER_LAT, origin.longitude + x / metresPerLon)

        fun point(xy: Pair<Double, Double>) = point(xy.first, xy.second)

        fun bearing(from: LatLon, to: LatLon): Double {
            val (ax, ay) = xy(from)
            val (bx, by) = xy(to)
            return (Math.toDegrees(atan2(bx - ax, by - ay)) + 360.0) % 360.0
        }

        /** The point of the segment closest to the origin, which is the fix. */
        fun closest(from: LatLon, to: LatLon): Pair<Double, Double> {
            val (ax, ay) = xy(from)
            val (bx, by) = xy(to)
            val dx = bx - ax
            val dy = by - ay
            val t = ((-ax * dx - ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            return ax + dx * t to ay + dy * t
        }

        companion object {
            const val METRES_PER_LAT = 110_574.0
        }
    }
}
