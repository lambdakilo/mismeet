package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFixTest {
    @Test
    fun mapsPlatformValuesToThePayload() {
        val fix = LocationFix(
            timeMillis = 1_759_840_101_700, latitude = 60.1698561, longitude = 24.9383794, horizontalAccuracy = 11.6,
            ellipsoidalAltitude = 20.6, speed = 1.44, course = 359.6,
        )
        val payload = fix.payload(0.5)
        assertEquals(1_759_840_101L, payload.fixTime)
        assertEquals(12, payload.accuracy)
        assertEquals(21, payload.altitude)
        assertEquals(0, payload.course)
        assertEquals(
            "{\"v\":1,\"ts\":1759840101,\"lat\":60.169856,\"lon\":24.938379,\"acc\":12,\"alt\":21,\"spd\":1.4,\"hdg\":0,\"bat\":0.5}",
            payload.encode(),
        )
    }

    @Test
    fun omitsUnknownValues() {
        val fix = LocationFix(timeMillis = 5_000, latitude = 1.0, longitude = 2.0, horizontalAccuracy = 30.0)
        assertEquals("{\"v\":1,\"ts\":5,\"lat\":1,\"lon\":2,\"acc\":30}", fix.payload(null).encode())
    }
}
