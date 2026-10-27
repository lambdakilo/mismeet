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
    fun roundTripsThroughLinkAndNprofileUri() {
        val relays = listOf(RelayUrl.parse("wss://relay.example.com"), RelayUrl.parse("wss://nos.example.org"))
        val link = Invite(keys.publicKey(), relays).link()
        assertTrue(link, link.startsWith("https://zululima.github.io/mismeet/i#nprofile1"))
        val parsed = Invite.parse(link)
        assertEquals(keys.publicKey().toHex(), parsed.publicKey.toHex())
        assertEquals(relays.map { it.toString() }, urls(parsed))
        val nprofile = link.removePrefix(ProtocolConstants.INVITE_LINK_PREFIX)
        assertEquals(urls(parsed), urls(Invite.parse(nprofile)))
        assertEquals(urls(parsed), urls(Invite.parse("nostr:$nprofile")))
    }

    @Test
    fun acceptsEarlierInviteLink() {
        val relays = listOf(RelayUrl.parse("wss://relay.example.com"))
        val nprofile = Invite(keys.publicKey(), relays).link().removePrefix(ProtocolConstants.INVITE_LINK_PREFIX)
        val parsed = Invite.parse("https://lambdakilo.github.io/mismeet/i#$nprofile")
        assertEquals(keys.publicKey().toHex(), parsed.publicKey.toHex())
        assertEquals(relays.map { it.toString() }, urls(parsed))
    }

    @Test
    fun keepsAtMostFourRelays() {
        val relays = (1..6).map { RelayUrl.parse("wss://relay$it.example.com") }
        val parsed = Invite.parse(Invite(keys.publicKey(), relays).link())
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
