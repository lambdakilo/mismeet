import XCTest
import NostrSDK
@testable import Mismeet

final class InviteTests: XCTestCase {
    private let keys = Keys.generate()

    func testRoundTripsThroughLinkAndNprofileUri() throws {
        let relays = [try RelayUrl.parse(url: "wss://relay.example.com"), try RelayUrl.parse(url: "wss://nos.example.org")]
        let link = try Invite(publicKey: keys.publicKey(), relays: relays).link()
        XCTAssertTrue(link.hasPrefix("https://zululima.github.io/mismeet/i#nprofile1"))
        let parsed = try Invite.parse(link)
        XCTAssertEqual(parsed.publicKey, keys.publicKey())
        XCTAssertEqual(parsed.relays, relays)
        let nprofile = String(link.dropFirst(ProtocolConstants.inviteLinkPrefix.count))
        XCTAssertEqual(try Invite.parse(nprofile), parsed)
        XCTAssertEqual(try Invite.parse("nostr:\(nprofile)"), parsed)
    }

    func testAcceptsEarlierInviteLink() throws {
        let invite = Invite(publicKey: keys.publicKey(), relays: [try RelayUrl.parse(url: "wss://relay.example.com")])
        let nprofile = String(try invite.link().dropFirst(ProtocolConstants.inviteLinkPrefix.count))
        XCTAssertEqual(try Invite.parse("https://lambdakilo.github.io/mismeet/i#\(nprofile)"), invite)
    }

    func testKeepsAtMostFourRelays() throws {
        let relays = try (1...6).map { try RelayUrl.parse(url: "wss://relay\($0).example.com") }
        let parsed = try Invite.parse(try Invite(publicKey: keys.publicKey(), relays: relays).link())
        XCTAssertEqual(parsed.relays, Array(relays.prefix(4)))
    }

    func testAcceptsNpubWithAndWithoutScheme() throws {
        let npub = try keys.publicKey().toBech32()
        XCTAssertEqual(try Invite.parse(npub), Invite(publicKey: keys.publicKey(), relays: []))
        XCTAssertEqual(try Invite.parse(" nostr:\(npub)\n"), Invite(publicKey: keys.publicKey(), relays: []))
    }

    func testRejectsOtherEntities() throws {
        let nsec = try keys.secretKey().toBech32()
        XCTAssertThrowsError(try Invite.parse(nsec))
        XCTAssertThrowsError(try Invite.parse("hello"))
    }
}
