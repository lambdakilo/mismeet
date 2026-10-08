package app.mismeet.protocol

import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl

data class ContactRelays(val publicKey: PublicKey, val relays: List<RelayUrl>)

/** Groups contacts by relay so that each relay is asked only about its own authors, spec section 7.3. */
object RelayPlan {
    fun authorsByRelay(contacts: List<ContactRelays>): Map<RelayUrl, List<PublicKey>> {
        val plan = LinkedHashMap<RelayUrl, MutableList<PublicKey>>()
        for (contact in contacts) {
            for (relay in contact.relays.take(ProtocolConstants.MAX_RELAYS_PER_CONTACT)) {
                val authors = plan.getOrPut(relay) { mutableListOf() }
                if (authors.none { it.toHex() == contact.publicKey.toHex() }) authors += contact.publicKey
            }
        }
        return plan
    }
}
