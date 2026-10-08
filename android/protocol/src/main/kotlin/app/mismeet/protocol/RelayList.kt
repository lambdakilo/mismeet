package app.mismeet.protocol

import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.Tag
import org.nostrdevkit.sdk.Timestamp

/** Kind 10002 relay lists, spec section 7.1. */
object RelayList {
    fun event(relays: List<String>, keys: Keys, now: Long): Event {
        val tags = relays.map { Tag.parse(listOf("r", it)) }
        val unsigned = EventBuilder(Kind(ProtocolConstants.RELAY_LIST_KIND), "")
            .tags(tags)
            .customCreatedAt(Timestamp.fromSecs(now.toULong()))
            .finalizeUnsigned(keys.publicKey())
        return keys.signEvent(unsigned)
    }

    /** The relays the author writes to: `r` tags without a marker or marked `write`, at most eight. */
    fun writeRelays(event: Event): List<String> {
        val urls = mutableListOf<String>()
        for (tag in event.tags()) {
            val parts = tag.toVec()
            if (parts.size < 2 || parts[0] != "r" || (parts.size > 2 && parts[2] != "write")) continue
            val url = runCatching { RelayUrl.parse(parts[1]) }.getOrNull() ?: continue
            val text = url.toString()
            if (text !in urls) urls += text
        }
        return urls.take(ProtocolConstants.MAX_RELAYS_PER_CONTACT)
    }
}
