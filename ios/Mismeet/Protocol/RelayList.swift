import Foundation
import NostrSDK

/// Kind 10002 relay lists, spec section 7.1.
enum RelayList {
    static func event(relays: [String], keys: Keys, now: UInt64) throws -> Event {
        let tags = try relays.map { try Tag.parse(data: ["r", $0]) }
        let unsigned = EventBuilder(kind: Kind(kind: ProtocolConstants.relayListKind), content: "")
            .tags(tags: tags)
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: now))
            .finalizeUnsigned(publicKey: keys.publicKey())
        return try keys.signEvent(unsignedEvent: unsigned)
    }

    /// The relays the author writes to: `r` tags without a marker or marked `write`, at most eight.
    static func writeRelays(of event: Event) -> [String] {
        var urls: [String] = []
        for tag in event.tags() {
            let parts = tag.toVec()
            guard parts.count >= 2, parts[0] == "r", parts.count == 2 || parts[2] == "write" else { continue }
            guard let url = try? RelayUrl.parse(url: parts[1]) else { continue }
            let text = url.description
            if !urls.contains(text) { urls.append(text) }
        }
        return Array(urls.prefix(ProtocolConstants.maxRelaysPerContact))
    }
}
