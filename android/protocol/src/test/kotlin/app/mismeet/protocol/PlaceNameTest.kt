package app.mismeet.protocol

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNameTest {
    // University of Helsinki's Kumpula campus, in cell (602, 124).
    private val campus = LatLon(60.2045, 24.9625)
    private val metresPerLon = cos(Math.toRadians(campus.latitude)) * 111_320.0

    private fun place(id: Long, place: String, name: String, x: Double, y: Double) =
        Place(id, place, name, LatLon(campus.latitude + y / 110_574.0, campus.longitude + x / metresPerLon))

    @Test
    fun cellAndQuery() {
        assertEquals(PlaceCell(602, 124), PlaceCell.of(campus))
        assertEquals(
            "[out:json][timeout:25];\nnode[\"place\"~\"^(suburb|town|village|city)$\"][\"name\"](60.2,24.8,60.3,25.0);\nout;",
            PlaceNames.query(PlaceCell.of(campus)),
        )
        assertEquals("-33.9,151.2,-33.8,151.4", PlaceCell.of(LatLon(-33.87, 151.21)).bbox())
    }

    @Test
    fun loadsTheCellsFiveKilometresAround() {
        assertEquals(
            listOf(PlaceCell(601, 124), PlaceCell(601, 125), PlaceCell(602, 124), PlaceCell(602, 125)),
            PlaceCell.around(campus),
        )
        assertEquals(listOf(PlaceCell(602, 124)), PlaceCell.around(LatLon(60.25, 24.9)))
    }

    @Test
    fun parseKeepsNamedPlaceNodes() {
        val json = """
            {"elements":[
              {"type":"node","id":42,"lat":60.2093602,"lon":24.9646243,"tags":{"name":"Kumpula","name:sv":"Gumtäkt","place":"suburb"}},
              {"type":"node","id":43,"lat":60.2,"lon":24.9,"tags":{"place":"village"}},
              {"type":"way","id":44,"tags":{"name":"Kumpula","place":"suburb"}}
            ]}
        """.trimIndent()
        assertEquals(listOf(Place(42, "suburb", "Kumpula", LatLon(60.2093602, 24.9646243))), PlaceNames.parse(json))
    }

    @Test
    fun namesTheDistrictRatherThanTheCity() {
        val places = listOf(
            place(1, "city", "Helsinki", 300.0, 0.0),
            place(2, "suburb", "Kumpula", 0.0, 800.0),
            place(3, "neighbourhood", "Kumpulan kampus", 0.0, 90.0),
        )
        assertEquals("Kumpula", PlaceNames.name(campus, places))
    }

    @Test
    fun townAndVillageCountAsDistricts() {
        assertEquals("Near", PlaceNames.name(campus, listOf(place(1, "suburb", "Far", 2_000.0, 0.0), place(2, "town", "Near", 0.0, -1_500.0))))
        assertEquals("Kylä", PlaceNames.name(campus, listOf(place(1, "village", "Kylä", 4_900.0, 0.0), place(2, "city", "Kaupunki", 100.0, 0.0))))
    }

    @Test
    fun fallsBackToTheCityAndThenToNothing() {
        assertEquals("Helsinki", PlaceNames.name(campus, listOf(place(1, "suburb", "Far", 5_100.0, 0.0), place(2, "city", "Helsinki", 0.0, 3_000.0))))
        assertNull(PlaceNames.name(campus, listOf(place(1, "suburb", "Far", 5_100.0, 0.0), place(2, "city", "Helsinki", 0.0, -5_100.0))))
        assertNull(PlaceNames.name(campus, emptyList()))
    }

    @Test
    fun tieGoesToTheLowerId() {
        assertEquals("West", PlaceNames.name(campus, listOf(place(7, "suburb", "East", 1_000.0, 0.0), place(5, "suburb", "West", -1_000.0, 0.0))))
    }

    @Test
    fun kumpulaCampusFromOpenStreetMap() {
        val places = listOf(
            Place(1, "suburb", "Kumpula", LatLon(60.2093602, 24.9646243)),
            Place(2, "suburb", "Toukola", LatLon(60.2084442, 24.9721468)),
            Place(3, "suburb", "Hermanni", LatLon(60.1967824, 24.9672281)),
            Place(4, "suburb", "Vallila", LatLon(60.1944516, 24.9564189)),
            Place(5, "city", "Helsinki", LatLon(60.1674, 24.9426)),
        )
        assertEquals("Kumpula", PlaceNames.name(campus, places))
    }
}
