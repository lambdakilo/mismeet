package app.mismeet.android.location

import app.mismeet.protocol.LatLon
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountriesTest {
    private val countries = Countries(File("src/main/assets/countries.json").readText())

    @Test
    fun findsTheCountryOfAPoint() {
        assertEquals("FI", countries.code(HELSINKI))
        assertEquals("EE", countries.code(TALLINN))
        assertEquals("SE", countries.code(STOCKHOLM))
        assertEquals("ZA", countries.code(LatLon(-26.20, 28.04)))
        assertNull(countries.code(BALTIC_SEA))
    }

    @Test
    fun leavesOutAHoleInAnotherCountry() {
        assertEquals("LS", countries.code(LatLon(-29.31, 27.48)))
    }

    @Test
    fun opensOnThisPhonesCountryOrElseWhereMostContactsAre() {
        assertEquals("EE", countries.home(TALLINN, listOf(HELSINKI, HELSINKI)))
        assertEquals("FI", countries.home(null, listOf(STOCKHOLM, HELSINKI, HELSINKI)))
        assertEquals("FI", countries.home(BALTIC_SEA, listOf(STOCKHOLM, HELSINKI, HELSINKI)))
        assertEquals("SE", countries.home(null, listOf(STOCKHOLM, HELSINKI, HELSINKI, STOCKHOLM)))
        assertEquals("FI", countries.home(null, listOf(HELSINKI, STOCKHOLM)))
        assertNull(countries.home(null, listOf(BALTIC_SEA)))
    }

    private companion object {
        val HELSINKI = LatLon(60.17, 24.94)
        val TALLINN = LatLon(59.437, 24.754)
        val STOCKHOLM = LatLon(59.33, 18.07)
        val BALTIC_SEA = LatLon(58.5, 20.0)
    }
}
