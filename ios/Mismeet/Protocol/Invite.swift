import Foundation
import NostrSDK

/// A contact invite, spec section 4.1: a `nostr:nprofile` URI, or a bare `nprofile` or `npub`.
struct Invite: Equatable {
    var publicKey: PublicKey
    var relays: [RelayUrl]

    enum ParseError: Error, Equatable {
        case notAProfile
    }

    func uri() throws -> String {
        let relays = Array(relays.prefix(ProtocolConstants.maxRelaysPerList))
        return try Nip19Profile(publicKey: publicKey, relays: relays).toNostrUri()
    }

    static func parse(_ text: String) throws -> Invite {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("nostr:") {
            switch try Nip21.parse(uri: trimmed).asEnum() {
            case .pubkey(let publicKey):
                return Invite(publicKey: publicKey, relays: [])
            case .profile(let profile):
                return Invite(publicKey: profile.publicKey(), relays: profile.relays())
            default:
                throw ParseError.notAProfile
            }
        }
        switch try Nip19.fromBech32(bech32: trimmed).asEnum() {
        case .pubkey(let publicKey):
            return Invite(publicKey: publicKey, relays: [])
        case .profile(let profile):
            return Invite(publicKey: profile.publicKey(), relays: profile.relays())
        default:
            throw ParseError.notAProfile
        }
    }
}
