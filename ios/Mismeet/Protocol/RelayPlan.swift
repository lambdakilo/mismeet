import Foundation
import NostrSDK

struct ContactRelays {
    var publicKey: PublicKey
    var relays: [RelayUrl]
}

/// Groups contacts by relay so that each relay is asked only about its own authors, spec section 7.3.
enum RelayPlan {
    static func authorsByRelay(_ contacts: [ContactRelays]) -> [RelayUrl: [PublicKey]] {
        var plan: [RelayUrl: [PublicKey]] = [:]
        for contact in contacts {
            for relay in contact.relays.prefix(ProtocolConstants.maxRelaysPerContact) {
                if plan[relay, default: []].contains(contact.publicKey) { continue }
                plan[relay, default: []].append(contact.publicKey)
            }
        }
        return plan
    }
}
