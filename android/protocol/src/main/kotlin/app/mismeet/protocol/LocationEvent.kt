package app.mismeet.protocol

import java.security.SecureRandom
import java.util.Collections
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.Nip44Version
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.Tag
import org.nostrdevkit.sdk.Timestamp
import org.nostrdevkit.sdk.nip44Decrypt
import org.nostrdevkit.sdk.nip44Encrypt

class TooManyRecipientsException(val count: Int) :
    Exception("$count recipients, at most ${ProtocolConstants.MAX_SHARING_CONTACTS}")

enum class LocationEventRejection {
    WRONG_AUTHOR,
    WRONG_KIND,
    WRONG_IDENTIFIER,
    INVALID_SIGNATURE,
    MISSING_EXPIRATION,
    MALFORMED_EXPIRATION,
    EXPIRED,
    MALFORMED_CONTENT,
    UNSUPPORTED_CONTENT_VERSION,
}

class RejectedEventException(val rejection: LocationEventRejection) : Exception(rejection.name)

sealed class LocationReadResult {
    data class Shared(val payload: LocationPayload) : LocationReadResult()
    data object NotSharing : LocationReadResult()
}

/** Builds location events, spec sections 5 and 7.2. */
object LocationEventBuilder {
    private val random = SecureRandom()
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun bucketSize(recipients: Int): Int =
        ProtocolConstants.ENTRY_BUCKETS.firstOrNull { it >= recipients }
            ?: throw TooManyRecipientsException(recipients)

    fun createdAt(now: Long, previous: Long?): Long =
        if (previous == null) now else maxOf(now, previous + 1)

    fun entries(plaintext: String, recipients: List<PublicKey>, keys: Keys): List<String> {
        val bucket = bucketSize(recipients.size)
        val secretKey = keys.secretKey()
        val entries = recipients.mapTo(mutableListOf()) {
            nip44Encrypt(secretKey, it, plaintext, Nip44Version.V2)
        }
        val fillerLength = plaintext.toByteArray(Charsets.UTF_8).size
        repeat(bucket - recipients.size) {
            val throwaway = Keys.generate()
            entries += nip44Encrypt(secretKey, throwaway.publicKey(), randomFiller(fillerLength), Nip44Version.V2)
        }
        Collections.shuffle(entries, random)
        return entries
    }

    fun content(entries: List<String>): String =
        entries.joinToString(",", "{\"v\":${ProtocolConstants.CONTENT_VERSION},\"c\":[", "]}") { "\"$it\"" }

    fun build(
        payload: LocationPayload,
        recipients: List<PublicKey>,
        keys: Keys,
        now: Long,
        previousCreatedAt: Long?,
    ): Event = sign(entries(payload.encode(), recipients, keys), keys, createdAt(now, previousCreatedAt))

    /** The all-dummy event of spec section 8.3, published when a contact is removed and no recent fix exists. */
    fun buildRevocation(keys: Keys, now: Long, previousCreatedAt: Long?): Event = sign(
        entries(randomFiller(ProtocolConstants.REVOCATION_FILLER_LENGTH), emptyList(), keys),
        keys,
        createdAt(now, previousCreatedAt),
    )

    fun randomFiller(length: Int): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    private fun sign(entries: List<String>, keys: Keys, createdAt: Long): Event {
        val tags = listOf(
            Tag.identifier(ProtocolConstants.IDENTIFIER),
            Tag.expiration(Timestamp.fromSecs((createdAt + ProtocolConstants.EXPIRATION_SECONDS).toULong())),
        )
        val unsigned = EventBuilder(Kind(ProtocolConstants.LOCATION_KIND), content(entries))
            .tags(tags)
            .customCreatedAt(Timestamp.fromSecs(createdAt.toULong()))
            .finalizeUnsigned(keys.publicKey())
        return keys.signEvent(unsigned)
    }
}

/** Reads location events, spec sections 5.5, 7.3 and 8.4. */
object LocationEventReader {
    fun expiration(event: Event): Long {
        val tag = event.tags().firstOrNull { it.kind() == "expiration" }
            ?: throw RejectedEventException(LocationEventRejection.MISSING_EXPIRATION)
        return tag.content()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw RejectedEventException(LocationEventRejection.MALFORMED_EXPIRATION)
    }

    fun entries(event: Event, contact: PublicKey, now: Long): List<String> {
        if (event.author().toHex() != contact.toHex()) throw RejectedEventException(LocationEventRejection.WRONG_AUTHOR)
        if (event.kind().asU16() != ProtocolConstants.LOCATION_KIND) throw RejectedEventException(LocationEventRejection.WRONG_KIND)
        val identifier = event.tags().firstOrNull { it.kind() == "d" }?.content()
        if (identifier != ProtocolConstants.IDENTIFIER) throw RejectedEventException(LocationEventRejection.WRONG_IDENTIFIER)
        if (!event.verify()) throw RejectedEventException(LocationEventRejection.INVALID_SIGNATURE)
        if (expiration(event) <= now) throw RejectedEventException(LocationEventRejection.EXPIRED)
        return parseContent(event.content())
    }

    fun parseContent(content: String): List<String> {
        val element = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            throw RejectedEventException(LocationEventRejection.MALFORMED_CONTENT)
        }
        val fields = element as? JsonObject ?: throw RejectedEventException(LocationEventRejection.MALFORMED_CONTENT)
        val version = (fields["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (version != ProtocolConstants.CONTENT_VERSION) {
            throw RejectedEventException(LocationEventRejection.UNSUPPORTED_CONTENT_VERSION)
        }
        val list = fields["c"] as? JsonArray ?: throw RejectedEventException(LocationEventRejection.MALFORMED_CONTENT)
        return list.map { entry ->
            (entry as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw RejectedEventException(LocationEventRejection.MALFORMED_CONTENT)
        }
    }

    fun read(event: Event, contact: PublicKey, keys: Keys, now: Long): LocationReadResult {
        val secretKey = keys.secretKey()
        for (entry in entries(event, contact, now)) {
            val plaintext = try {
                nip44Decrypt(secretKey, contact, entry)
            } catch (e: Exception) {
                continue
            }
            val payload = try {
                LocationPayload.decode(plaintext)
            } catch (e: LocationPayload.DecodeException) {
                continue
            }
            return LocationReadResult.Shared(payload)
        }
        return LocationReadResult.NotSharing
    }

    /**
     * The event to read among those fetched for one contact: unexpired, newer than the cache,
     * greatest `created_at`, lowest id on a tie.
     */
    fun newest(events: List<Event>, now: Long, cachedCreatedAt: Long?): Event? = events
        .filter { event ->
            val expiration = try {
                expiration(event)
            } catch (e: RejectedEventException) {
                return@filter false
            }
            expiration > now && (cachedCreatedAt == null || event.createdAt().asSecs().toLong() > cachedCreatedAt)
        }
        .minWithOrNull(compareByDescending<Event> { it.createdAt().asSecs() }.thenBy { it.id().toHex() })
}
