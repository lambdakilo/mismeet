package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.RelayUrl

class RelayPlanTest {
    @Test
    fun eachRelayGetsOnlyItsOwnAuthors() {
        val alice = Keys.generate().publicKey()
        val bob = Keys.generate().publicKey()
        val one = RelayUrl.parse("wss://one.example.com")
        val two = RelayUrl.parse("wss://two.example.com")
        val plan = RelayPlan.authorsByRelay(
            listOf(ContactRelays(alice, listOf(one, two)), ContactRelays(bob, listOf(two, two))),
        ).mapKeys { it.key.toString() }.mapValues { entry -> entry.value.map { it.toHex() } }
        assertEquals(2, plan.size)
        assertEquals(listOf(alice.toHex()), plan[one.toString()])
        assertEquals(listOf(alice.toHex(), bob.toHex()), plan[two.toString()])
    }

    @Test
    fun capsRelaysPerContact() {
        val alice = Keys.generate().publicKey()
        val relays = (1..10).map { RelayUrl.parse("wss://relay$it.example.com") }
        val plan = RelayPlan.authorsByRelay(listOf(ContactRelays(alice, relays))).mapKeys { it.key.toString() }
        assertEquals(ProtocolConstants.MAX_RELAYS_PER_CONTACT, plan.size)
        assertNull(plan[relays[9].toString()])
    }
}
