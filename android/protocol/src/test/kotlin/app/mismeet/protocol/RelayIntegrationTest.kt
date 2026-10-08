package app.mismeet.protocol

import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.nostrdevkit.sdk.AckPolicy
import org.nostrdevkit.sdk.Client
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.ReqExitPolicy
import org.nostrdevkit.sdk.ReqTarget
import org.nostrdevkit.sdk.SendEventTarget

/**
 * Talks to public relays, so it runs only when MISMEET_RELAY_TESTS=1 is in the environment.
 * It publishes one location event and one relay list under throwaway keys and reads them back.
 */
class RelayIntegrationTest {
    private val relayUrls = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

    private suspend fun connectedClient(): Client {
        val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
        relayUrls.forEach { client.addRelay(RelayUrl.parse(it), null, false, null) }
        val output = client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
        println("connected: ${output.success} failed: ${output.failed}")
        return client
    }

    @Test
    fun publishesAndReadsBackThroughPublicRelays() = runBlocking {
        assumeTrue(System.getenv("MISMEET_RELAY_TESTS") == "1")
        val publisher = Keys.generate()
        val reader = Keys.generate()
        val now = System.currentTimeMillis() / 1000
        val payload = LocationPayload(fixTime = now, latitude = 60.1699, longitude = 24.9384, accuracy = 10)
        val location = LocationEventBuilder.build(payload, listOf(reader.publicKey()), publisher, now, null)
        val relayList = RelayList.event(relayUrls, publisher, now)

        val writer = connectedClient()
        try {
            for (event in listOf(location, relayList)) {
                val output = writer.sendEvent(
                    event, SendEventTarget.broadcast(), AckPolicy.all(),
                    Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), null,
                )
                println("kind ${event.kind().asU16()} accepted by ${output.success} rejected by ${output.failed}")
                assertTrue("kind ${event.kind().asU16()}: ${output.failed}", output.success.isNotEmpty())
            }
        } finally {
            writer.shutdown()
        }

        val targets = relayUrls.associate {
            RelayUrl.parse(it) to listOf(
                Filter().kinds(listOf(Kind(ProtocolConstants.LOCATION_KIND))).authors(listOf(publisher.publicKey())).identifier(ProtocolConstants.IDENTIFIER),
                Filter().kinds(listOf(Kind(ProtocolConstants.RELAY_LIST_KIND))).authors(listOf(publisher.publicKey())),
            )
        }
        val fetcher = connectedClient()
        val events = try {
            fetcher.fetchEvents(ReqTarget.manual(targets), Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), ReqExitPolicy.ExitOnEose, null)
        } finally {
            fetcher.shutdown()
        }
        println("fetched ${events.size} events: ${events.map { it.kind().asU16() }}")
        val newest = LocationEventReader.newest(events.filter { it.kind().asU16() == ProtocolConstants.LOCATION_KIND }, now, null)
        assertNotNull("no location event came back", newest)
        assertEquals(LocationReadResult.Shared(payload), LocationEventReader.read(newest!!, publisher.publicKey(), reader, now))
        assertEquals(LocationReadResult.NotSharing, LocationEventReader.read(newest, publisher.publicKey(), Keys.generate(), now))
        val list = events.firstOrNull { it.kind().asU16() == ProtocolConstants.RELAY_LIST_KIND }
        assertNotNull("no relay list came back", list)
        assertEquals(relayUrls.map { RelayUrl.parse(it).toString() }, RelayList.writeRelays(list!!))
    }
}
