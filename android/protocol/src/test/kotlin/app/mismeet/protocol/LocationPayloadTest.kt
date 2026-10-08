package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationPayloadTest {
    private val full = LocationPayload(
        fixTime = 1_759_840_101, latitude = 60.169856, longitude = 24.938379, accuracy = 12,
        altitude = 21, speed = 1.4, course = 270, battery = 0.73,
    )

    @Test
    fun encodesAllKeysInSpecOrder() {
        assertEquals(
            "{\"v\":1,\"ts\":1759840101,\"lat\":60.169856,\"lon\":24.938379,\"acc\":12,\"alt\":21,\"spd\":1.4,\"hdg\":270,\"bat\":0.73}",
            full.encode(),
        )
    }

    @Test
    fun omitsUnknownOptionalKeys() {
        val payload = LocationPayload(fixTime = 1, latitude = 60.5, longitude = -24.0, accuracy = 0)
        assertEquals("{\"v\":1,\"ts\":1,\"lat\":60.5,\"lon\":-24,\"acc\":0}", payload.encode())
    }

    @Test
    fun roundsCoordinatesToSixDecimalsWithoutExponent() {
        val payload = LocationPayload(
            fixTime = 1, latitude = 60.1698561234, longitude = -24.9383794, accuracy = 3,
            speed = 1.44, battery = 0.731,
        )
        assertEquals("{\"v\":1,\"ts\":1,\"lat\":60.169856,\"lon\":-24.938379,\"acc\":3,\"spd\":1.4,\"bat\":0.73}", payload.encode())
        assertEquals("0", JsonNumber.format(0.0000001, 6))
        assertEquals("0", JsonNumber.format(-0.0000001, 6))
        assertEquals("123456789.5", JsonNumber.format(123456789.5, 1))
    }

    @Test
    fun decodesWhatItEncodes() {
        assertEquals(full, LocationPayload.decode(full.encode()))
    }

    @Test
    fun ignoresUnknownKeysAndInvalidOptionals() {
        val decoded = LocationPayload.decode(
            "{\"v\":1,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3,\"future\":\"x\",\"alt\":\"high\",\"hdg\":400,\"bat\":2}",
        )
        assertEquals(LocationPayload(fixTime = 5, latitude = 1.5, longitude = 2.5, accuracy = 3), decoded)
    }

    @Test
    fun rejectsWrongVersionAndMissingOrMistypedRequiredKeys() {
        fun reason(json: String): Pair<LocationPayload.Reason, String?> {
            val e = assertThrows(LocationPayload.DecodeException::class.java) { LocationPayload.decode(json) }
            return e.reason to e.field
        }
        assertEquals(LocationPayload.Reason.UNSUPPORTED_VERSION to null, reason("{\"v\":2,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}"))
        assertEquals(LocationPayload.Reason.UNSUPPORTED_VERSION to null, reason("{\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}"))
        assertEquals(LocationPayload.Reason.INVALID to "lat", reason("{\"v\":1,\"ts\":5,\"lat\":\"1.5\",\"lon\":2.5,\"acc\":3}"))
        assertEquals(LocationPayload.Reason.INVALID to "acc", reason("{\"v\":1,\"ts\":5,\"lat\":1.5,\"lon\":2.5}"))
        assertEquals(LocationPayload.Reason.UNSUPPORTED_VERSION to null, reason("{\"v\":true,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}"))
        assertEquals(LocationPayload.Reason.NOT_AN_OBJECT to null, reason("[1]"))
        assertEquals(LocationPayload.Reason.NOT_AN_OBJECT to null, reason("not json"))
    }
}
