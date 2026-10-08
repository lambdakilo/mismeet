package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.RelayUrl

class InviteTest {
    private val keys = Keys.generate()

    private fun urls(invite: Invite) = invite.relays.map { it.toString() }

    @Test
    fun roundTripsThroughNprofileUri() {
        val relays = listOf(RelayUrl.parse("wss://relay.example.com"), RelayUrl.parse("wss://nos.example.org"))
        val uri = Invite(keys.publicKey(), relays).uri()
        assertTrue(uri, uri.startsWith("nostr:nprofile1"))
        val parsed = Invite.parse(uri)
        assertEquals(keys.publicKey().toHex(), parsed.publicKey.toHex())
        assertEquals(relays.map { it.toString() }, urls(parsed))
        assertEquals(urls(parsed), urls(Invite.parse(uri.removePrefix("nostr:"))))
    }

    @Test
    fun keepsAtMostFourRelays() {
        val relays = (1..6).map { RelayUrl.parse("wss://relay$it.example.com") }
        val parsed = Invite.parse(Invite(keys.publicKey(), relays).uri())
        assertEquals(relays.take(4).map { it.toString() }, urls(parsed))
    }

    @Test
    fun acceptsNpubWithAndWithoutScheme() {
        val npub = keys.publicKey().toBech32()
        val bare = Invite.parse(npub)
        assertEquals(keys.publicKey().toHex(), bare.publicKey.toHex())
        assertEquals(emptyList<String>(), urls(bare))
        val withScheme = Invite.parse(" nostr:$npub\n")
        assertEquals(keys.publicKey().toHex(), withScheme.publicKey.toHex())
        assertEquals(emptyList<String>(), urls(withScheme))
    }

    @Test
    fun rejectsOtherEntities() {
        assertThrows(Exception::class.java) { Invite.parse(keys.secretKey().toBech32()) }
        assertThrows(Exception::class.java) { Invite.parse("hello") }
    }
}
