import Foundation
import NostrSDK

/// A contact record, spec section 4.3. Everything here stays on the device.
struct Contact: Codable, Identifiable, Equatable {
    enum SharingStatus: String, Codable {
        case unknown, sharing, notSharing
    }

    var id: String
    var name: String
    var relays: [String]
    var share = true
    var addedAt: Date
    var sharing = SharingStatus.unknown
    var lastPayload: LocationPayload?
    var lastCreatedAt: UInt64?
    var lastEventId: String?
    var lastSeenAt: Date?
    var relayListCheckedAt: Date?

    var publicKey: PublicKey? {
        try? PublicKey.parse(publicKey: id)
    }

    var npubPrefix: String {
        String(((try? publicKey?.toBech32()) ?? id).prefix(12))
    }

    /// The relays to ask about this contact: its own, or the app's list until a kind 10002 is seen.
    func queryRelays(defaults: [String]) -> [RelayUrl] {
        (relays.isEmpty ? defaults : relays).compactMap { try? RelayUrl.parse(url: $0) }
    }
}
