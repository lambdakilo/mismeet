import XCTest
import NostrSDK
@testable import Mismeet

final class InviteTests: XCTestCase {
    private let keys = Keys.generate()

    func testRoundTripsThroughNprofileUri() throws {
        let relays = [try RelayUrl.parse(url: "wss://relay.example.com"), try RelayUrl.parse(url: "wss://nos.example.org")]
        let uri = try Invite(publicKey: keys.publicKey(), relays: relays).uri()
        XCTAssertTrue(uri.hasPrefix("nostr:nprofile1"))
        let parsed = try Invite.parse(uri)
        XCTAssertEqual(parsed.publicKey, keys.publicKey())
        XCTAssertEqual(parsed.relays, relays)
        XCTAssertEqual(try Invite.parse(String(uri.dropFirst("nostr:".count))), parsed)
    }

    func testKeepsAtMostFourRelays() throws {
        let relays = try (1...6).map { try RelayUrl.parse(url: "wss://relay\($0).example.com") }
        let parsed = try Invite.parse(try Invite(publicKey: keys.publicKey(), relays: relays).uri())
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
