package app.mismeet.protocol

/** The values of spec/SPEC.md section 10. Change the spec first. */
object ProtocolConstants {
    val LOCATION_KIND: UShort = 31122u
    val RELAY_LIST_KIND: UShort = 10002u
    const val IDENTIFIER = "location"
    const val CONTENT_VERSION = 1
    const val PAYLOAD_VERSION = 1
    const val EXPIRATION_SECONDS = 86_400L
    val ENTRY_BUCKETS = listOf(4, 8, 16, 32, 64)
    const val MAX_SHARING_CONTACTS = 64
    const val MIN_RELAYS_PER_LIST = 2
    const val MAX_RELAYS_PER_LIST = 4
    const val MAX_RELAYS_PER_CONTACT = 8
    const val RELAY_LIST_REFRESH_SECONDS = 86_400L
    const val PUBLISH_THROTTLE_SECONDS = 60L
    const val MAX_FIX_AGE_SECONDS = 3_600L
    const val ANDROID_PUBLISH_INTERVAL_SECONDS = 600L
    const val FOREGROUND_FETCH_INTERVAL_SECONDS = 60L
    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val OK_TIMEOUT_SECONDS = 10L
    const val PUBLISH_TIMEOUT_SECONDS = 25L
    const val RATE_LIMIT_BACKOFF_SECONDS = 300L
    const val COORDINATE_DECIMALS = 6
    const val REVOCATION_FILLER_LENGTH = 100
}
