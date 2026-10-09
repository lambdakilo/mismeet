package app.mismeet.android.store

import android.content.Context
import app.mismeet.protocol.LocationFix
import app.mismeet.protocol.LocationPayload
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl

/** A contact record, spec section 4.3. Everything here stays on the device. */
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val relays: List<String>,
    val share: Boolean = true,
    val addedAt: Long,
    val sharing: SharingStatus = SharingStatus.UNKNOWN,
    val lastPayload: LocationPayload? = null,
    val lastCreatedAt: Long? = null,
    val lastEventId: String? = null,
    val lastSeenAt: Long? = null,
    val relayListCheckedAt: Long? = null,
) {
    enum class SharingStatus { UNKNOWN, SHARING, NOT_SHARING }

    fun publicKey(): PublicKey? = runCatching { PublicKey.parse(id) }.getOrNull()

    fun npubPrefix(): String = (runCatching { publicKey()?.toBech32() }.getOrNull() ?: id).take(12)

    /** The relays to ask about this contact: its own, or the app's list until a kind 10002 is seen. */
    fun queryRelays(defaults: List<String>): List<RelayUrl> =
        relays.ifEmpty { defaults }.mapNotNull { runCatching { RelayUrl.parse(it) }.getOrNull() }
}

@Serializable
data class PersistedState(
    val contacts: List<Contact> = emptyList(),
    val relays: List<String> = DEFAULT_RELAYS,
    val lastPublishedCreatedAt: Long? = null,
    val lastPublishedAt: Long? = null,
    val lastFix: LocationFix? = null,
    val relayListPublishedAt: Long? = null,
    val sharingEnabled: Boolean = false,
) {
    companion object {
        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
    }
}

class StateStore(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): PersistedState =
        runCatching { json.decodeFromString(PersistedState.serializer(), file.readText()) }.getOrDefault(PersistedState())

    fun save(state: PersistedState) {
        val temp = File(file.parentFile, "state.json.tmp")
        temp.writeText(json.encodeToString(PersistedState.serializer(), state))
        if (!temp.renameTo(file)) file.writeText(temp.readText())
    }
}
