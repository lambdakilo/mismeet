package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.Tag
import org.nostrdevkit.sdk.Timestamp
import org.nostrdevkit.sdk.nip44Decrypt

class LocationEventTest {
    private val now = 1_759_840_123L
    private val publisher = Keys.generate()
    private val recipients = List(3) { Keys.generate() }
    private val outsider = Keys.generate()
    private val payload = LocationPayload(fixTime = 1_759_840_101, latitude = 60.169856, longitude = 24.938379, accuracy = 12)

    private fun recipientKeys(): List<PublicKey> = recipients.map { it.publicKey() }

    private fun craft(
        kind: UShort = ProtocolConstants.LOCATION_KIND,
        tags: List<Tag>,
        content: String,
        createdAt: Long = now,
    ): Event {
        val unsigned = EventBuilder(Kind(kind), content)
            .tags(tags)
            .customCreatedAt(Timestamp.fromSecs(createdAt.toULong()))
            .finalizeUnsigned(publisher.publicKey())
        return publisher.signEvent(unsigned)
    }

    private fun validTags(expiration: Long = now + ProtocolConstants.EXPIRATION_SECONDS): List<Tag> = listOf(
        Tag.identifier(ProtocolConstants.IDENTIFIER),
        Tag.expiration(Timestamp.fromSecs(expiration.toULong())),
    )

    private fun decryptAll(keys: Keys, entries: List<String>): List<String> = entries.mapNotNull {
        try {
            nip44Decrypt(keys.secretKey(), publisher.publicKey(), it)
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun bucketSizes() {
        val expected = mapOf(0 to 4, 1 to 4, 4 to 4, 5 to 8, 8 to 8, 9 to 16, 16 to 16, 17 to 32, 32 to 32, 33 to 64, 64 to 64)
        for ((count, bucket) in expected) {
            assertEquals("$count recipients", bucket, LocationEventBuilder.bucketSize(count))
        }
        val e = assertThrows(TooManyRecipientsException::class.java) { LocationEventBuilder.bucketSize(65) }
        assertEquals(65, e.count)
    }

    @Test
    fun createdAtIsStrictlyGreaterThanThePreviousOne() {
        assertEquals(now, LocationEventBuilder.createdAt(now, null))
        assertEquals(now, LocationEventBuilder.createdAt(now, now - 5))
        assertEquals(now + 1, LocationEventBuilder.createdAt(now, now))
        assertEquals(now + 101, LocationEventBuilder.createdAt(now, now + 100))
    }

    @Test
    fun buildsTheEventOfSectionFive() {
        val event = LocationEventBuilder.build(payload, recipientKeys(), publisher, now, null)
        assertTrue(event.verify())
        assertEquals(31122.toUShort(), event.kind().asU16())
        assertEquals(now.toULong(), event.createdAt().asSecs())
        assertEquals(listOf(listOf("d", "location"), listOf("expiration", (now + 86_400).toString())), event.tags().map { it.toVec() })

        val entries = LocationEventReader.parseContent(event.content())
        assertEquals(4, entries.size)
        assertEquals("dummies have the length of real entries", 1, entries.map { it.length }.toSet().size)

        for (recipient in recipients) {
            assertEquals(listOf(payload.encode()), decryptAll(recipient, entries))
        }
        assertEquals(emptyList<String>(), decryptAll(outsider, entries))
    }

    @Test
    fun revocationEventDecryptsForNobody() {
        val event = LocationEventBuilder.buildRevocation(publisher, now, now)
        assertEquals((now + 1).toULong(), event.createdAt().asSecs())
        assertEquals(4, LocationEventReader.parseContent(event.content()).size)
        for (recipient in recipients) {
            assertEquals(LocationReadResult.NotSharing, LocationEventReader.read(event, publisher.publicKey(), recipient, now))
        }
    }

    @Test
    fun readerReturnsThePayloadToRecipientsOnly() {
        val event = LocationEventBuilder.build(payload, recipientKeys(), publisher, now, null)
        for (recipient in recipients) {
            assertEquals(LocationReadResult.Shared(payload), LocationEventReader.read(event, publisher.publicKey(), recipient, now))
        }
        assertEquals(LocationReadResult.NotSharing, LocationEventReader.read(event, publisher.publicKey(), outsider, now))
    }

    @Test
    fun readerRejectsBadEvents() {
        val content = LocationEventBuilder.content(emptyList())
        fun rejection(event: Event, contact: PublicKey = publisher.publicKey(), at: Long = now): LocationEventRejection? = try {
            LocationEventReader.entries(event, contact, at)
            null
        } catch (e: RejectedEventException) {
            e.rejection
        }
        val good = craft(tags = validTags(), content = content)
        assertNull(rejection(good))
        assertEquals(LocationEventRejection.WRONG_AUTHOR, rejection(good, contact = outsider.publicKey()))
        assertEquals(LocationEventRejection.EXPIRED, rejection(good, at = now + 86_400))
        assertEquals(LocationEventRejection.WRONG_KIND, rejection(craft(kind = 1u, tags = validTags(), content = content)))
        assertEquals(
            LocationEventRejection.WRONG_IDENTIFIER,
            rejection(craft(tags = listOf(Tag.identifier("other"), validTags()[1]), content = content)),
        )
        assertEquals(LocationEventRejection.MISSING_EXPIRATION, rejection(craft(tags = listOf(validTags()[0]), content = content)))
        assertEquals(
            LocationEventRejection.MALFORMED_EXPIRATION,
            rejection(craft(tags = listOf(validTags()[0], Tag.parse(listOf("expiration", "soon"))), content = content)),
        )
        assertEquals(LocationEventRejection.UNSUPPORTED_CONTENT_VERSION, rejection(craft(tags = validTags(), content = "{\"v\":2,\"c\":[]}")))
        assertEquals(LocationEventRejection.MALFORMED_CONTENT, rejection(craft(tags = validTags(), content = "[]")))
        assertEquals(LocationEventRejection.MALFORMED_CONTENT, rejection(craft(tags = validTags(), content = "{\"v\":1,\"c\":[1]}")))
        assertEquals(LocationEventRejection.MALFORMED_CONTENT, rejection(craft(tags = validTags(), content = "not json")))
    }

    @Test
    fun newestPicksGreatestCreatedAtThenLowestId() {
        val content = LocationEventBuilder.content(emptyList())
        val older = craft(tags = validTags(), content = content, createdAt = now - 10)
        val newer = craft(tags = validTags(), content = content, createdAt = now)
        val tie = craft(tags = validTags(), content = LocationEventBuilder.content(listOf("x")), createdAt = now)
        val expired = craft(tags = validTags(expiration = now), content = content, createdAt = now + 5)

        assertEquals(newer.id().toHex(), LocationEventReader.newest(listOf(older, newer), now, null)?.id()?.toHex())
        assertEquals(newer.id().toHex(), LocationEventReader.newest(listOf(newer, older), now, null)?.id()?.toHex())
        val lowest = listOf(newer, tie).minBy { it.id().toHex() }
        assertEquals(lowest.id().toHex(), LocationEventReader.newest(listOf(newer, tie), now, null)?.id()?.toHex())
        assertEquals(newer.id().toHex(), LocationEventReader.newest(listOf(older, newer, expired), now, null)?.id()?.toHex())
        assertEquals(newer.id().toHex(), LocationEventReader.newest(listOf(older, newer), now, now - 10)?.id()?.toHex())
        assertNull(LocationEventReader.newest(listOf(older, newer), now, now))
        assertNull(LocationEventReader.newest(emptyList(), now, null))
    }
}
