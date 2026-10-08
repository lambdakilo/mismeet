package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.Tag

class RelayListTest {
    private val keys = Keys.generate()

    @Test
    fun relayListEventRoundTrips() {
        val relays = listOf("wss://one.example.com", "wss://two.example.com")
        val event = RelayList.event(relays, keys, 1_759_840_123)
        assertEquals(10002.toUShort(), event.kind().asU16())
        assertEquals("", event.content())
        assertTrue(event.verify())
        assertEquals(relays.map { RelayUrl.parse(it).toString() }, RelayList.writeRelays(event))
    }

    @Test
    fun keepsWriteRelaysOnlyAndCapsAtEight() {
        val tags = mutableListOf(Tag.parse(listOf("r", "wss://read.example.com", "read")), Tag.parse(listOf("r", "not a url")))
        for (index in 1..10) {
            tags += Tag.parse(listOf("r", "wss://relay$index.example.com", if (index % 2 == 0) "write" else ""))
        }
        val unsigned = EventBuilder(Kind(10002u), "").tags(tags).finalizeUnsigned(keys.publicKey())
        val urls = RelayList.writeRelays(keys.signEvent(unsigned))
        assertEquals(urls.toString(), 5, urls.size)
        assertFalse(urls.any { it.contains("read.example.com") })
        assertTrue(urls.all { it.contains("relay") })
    }
}
