package app.mismeet.protocol

import kotlin.math.cos
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadEstimateTest {
    // The middle of cell (6016, 1246), about 550 m from each of its edges.
    private val origin = LatLon(60.165, 24.93)
    private val metresPerLon = cos(Math.toRadians(origin.latitude)) * 111_320.0

    private fun at(x: Double, y: Double) = LatLon(origin.latitude + y / 110_574.0, origin.longitude + x / metresPerLon)

    private fun way(id: Long, vararg points: Pair<Double, Double>, tags: Map<String, String> = mapOf("highway" to "residential")) =
        RoadWay(id, tags, points.map { (x, y) -> at(x, y) })

    private fun estimate(
        ways: List<RoadWay>,
        speed: Double?,
        heading: Int?,
        age: Long,
        fix: LatLon = at(0.0, 5.0),
        loaded: Set<RoadCell>? = null,
        unavailable: Set<RoadCell> = emptySet(),
    ): EstimateResult {
        val cells = loaded ?: (ways.flatMap { it.points }.map(RoadCell::of) + RoadEstimator.startCells(fix)).toSet()
        return RoadEstimator.estimate(fix, speed, heading, age, RoadNetwork(ways), cells, unavailable)
    }

    private fun assertNear(expected: LatLon, actual: LatLon) {
        val dx = (actual.longitude - expected.longitude) * metresPerLon
        val dy = (actual.latitude - expected.latitude) * 110_574.0
        assertTrue("off by ${hypot(dx, dy)} m", hypot(dx, dy) < 1.0)
    }

    private fun EstimateResult.point() = (this as EstimateResult.Estimate).point

    @Test
    fun modeFollowsSpeed() {
        assertEquals(TravelMode.FOOT, TravelMode.of(1.0))
        assertEquals(TravelMode.FOOT, TravelMode.of(2.49))
        assertEquals(TravelMode.BIKE, TravelMode.of(2.5))
        assertEquals(TravelMode.BIKE, TravelMode.of(8.49))
        assertEquals(TravelMode.CAR, TravelMode.of(8.5))
    }

    @Test
    fun noEstimateWhenSlowWithoutHeadingOrOld() {
        val road = listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0))
        assertEquals(EstimateResult.None, estimate(road, 0.9, 90, 60))
        assertEquals(EstimateResult.None, estimate(road, 10.0, null, 60))
        assertEquals(EstimateResult.None, estimate(road, null, 90, 60))
        assertEquals(EstimateResult.None, estimate(road, 10.0, 90, 901))
    }

    @Test
    fun followsStraightRoad() {
        val result = estimate(listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0)), 10.0, 90, 60) as EstimateResult.Estimate
        assertEquals(TravelMode.CAR, result.mode)
        assertNear(at(0.0, 0.0), result.path.first())
        assertNear(at(600.0, 0.0), result.point)
    }

    @Test
    fun spanStopsAtFiveMinutes() {
        val result = estimate(listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0)), 5.0, 90, 600)
        assertNear(at(1500.0, 0.0), result.point())
    }

    @Test
    fun noEstimateWhenHeadingPointsAwayFromEveryRoad() {
        assertEquals(EstimateResult.None, estimate(listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0)), 10.0, 0, 60))
    }

    @Test
    fun takesTheLeastTurnAtAJunction() {
        val ways = listOf(
            way(1, -100.0 to 0.0, 300.0 to 0.0),
            way(2, 300.0 to 0.0, 300.0 to -300.0),
            way(3, 300.0 to 0.0, 600.0 to 100.0),
        )
        val result = estimate(ways, 10.0, 90, 60)
        val along = 300.0 / hypot(300.0, 100.0)
        assertNear(at(300.0 + 300.0 * along, 100.0 * along), result.point())
    }

    @Test
    fun stopsAtADeadEnd() {
        val result = estimate(listOf(way(1, -100.0 to 0.0, 200.0 to 0.0)), 10.0, 90, 60)
        assertNear(at(200.0, 0.0), result.point())
    }

    @Test
    fun carKeepsToOneway() {
        val oneway = listOf(way(1, 500.0 to 0.0, -100.0 to 0.0, tags = mapOf("highway" to "residential", "oneway" to "yes")))
        assertEquals(EstimateResult.None, estimate(oneway, 10.0, 90, 60))
        assertNear(at(300.0, 0.0), estimate(oneway, 5.0, 90, 60).point())
    }

    @Test
    fun bikeUsesCycleTracksButNotMotorways() {
        val motorway = listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0, tags = mapOf("highway" to "motorway")))
        assertEquals(EstimateResult.None, estimate(motorway, 5.0, 90, 60))
        val footway = listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0, tags = mapOf("highway" to "footway")))
        assertEquals(EstimateResult.None, estimate(footway, 5.0, 90, 60))
        val shared = listOf(way(1, -100.0 to 0.0, 2000.0 to 0.0, tags = mapOf("highway" to "footway", "bicycle" to "designated")))
        assertNear(at(300.0, 0.0), estimate(shared, 5.0, 90, 60).point())
    }

    @Test
    fun asksForTheNextCellAndStopsWhenItIsUnavailable() {
        val ways = listOf(way(1, -100.0 to 0.0, 800.0 to 0.0), way(2, 800.0 to 0.0, 1500.0 to 0.0))
        val home = setOf(RoadCell(6016, 1246))
        assertEquals(EstimateResult.NeedsCell(RoadCell(6016, 1247)), estimate(ways, 20.0, 90, 60, loaded = home))
        val stopped = estimate(ways, 20.0, 90, 60, loaded = home, unavailable = setOf(RoadCell(6016, 1247)))
        assertNear(at(800.0, 0.0), stopped.point())
    }

    @Test
    fun cellsAndQuery() {
        assertEquals(RoadCell(6016, 1246), RoadCell.of(LatLon(60.1699, 24.9384)))
        assertEquals("60.16,24.92,60.17,24.94", RoadCell(6016, 1246).bbox())
        assertTrue(Overpass.query(RoadCell(6016, 1246)).contains("(60.16,24.92,60.17,24.94);\nout tags geom;"))
    }

    @Test
    fun parsesOverpassWays() {
        val json = """
            {"version":0.6,"elements":[
              {"type":"way","id":42,"bounds":{},"geometry":[{"lat":60.1,"lon":24.9},{"lat":60.2,"lon":24.95}],
               "tags":{"highway":"cycleway","name":"Baana","foot":"designated"}},
              {"type":"way","id":43,"geometry":[{"lat":60.1,"lon":24.9}],"tags":{"highway":"path"}},
              {"type":"node","id":1,"lat":60.1,"lon":24.9}
            ]}
        """.trimIndent()
        val ways = Overpass.parse(json)
        assertEquals(1, ways.size)
        assertEquals(42L, ways[0].id)
        assertEquals(mapOf("highway" to "cycleway", "foot" to "designated"), ways[0].tags)
        assertEquals(listOf(LatLon(60.1, 24.9), LatLon(60.2, 24.95)), ways[0].points)
    }
}
