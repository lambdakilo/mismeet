import NostrSDK
import XCTest
@testable import Mismeet

final class RelayListTests: XCTestCase {
    private let keys = Keys.generate()

    func testRelayListEventRoundTrips() throws {
        let relays = ["wss://one.example.com", "wss://two.example.com"]
        let event = try RelayList.event(relays: relays, keys: keys, now: 1_759_840_123)
        XCTAssertEqual(event.kind().asU16(), 10002)
        XCTAssertEqual(event.content(), "")
        XCTAssertTrue(event.verify())
        XCTAssertEqual(RelayList.writeRelays(of: event), relays.map { try! RelayUrl.parse(url: $0).description })
    }

    func testKeepsWriteRelaysOnlyAndCapsAtEight() throws {
        var tags = [try Tag.parse(data: ["r", "wss://read.example.com", "read"]), try Tag.parse(data: ["r", "not a url"])]
        for index in 1...10 {
            tags.append(try Tag.parse(data: ["r", "wss://relay\(index).example.com", index % 2 == 0 ? "write" : ""]))
        }
        let unsigned = EventBuilder(kind: Kind(kind: 10002), content: "").tags(tags: tags).finalizeUnsigned(publicKey: keys.publicKey())
        let event = try keys.signEvent(unsignedEvent: unsigned)
        let urls = RelayList.writeRelays(of: event)
        XCTAssertEqual(urls.count, 5, "\(urls)")
        XCTAssertFalse(urls.contains { $0.contains("read.example.com") })
        XCTAssertTrue(urls.allSatisfy { $0.contains("relay") })
    }

    func testContactFallsBackToDefaultRelays() {
        let contact = Contact(id: keys.publicKey().toHex(), name: "A", relays: [], addedAt: Date())
        XCTAssertEqual(contact.queryRelays(defaults: ["wss://d.example.com"]).map(\.description), ["wss://d.example.com"])
        let own = Contact(id: keys.publicKey().toHex(), name: "A", relays: ["wss://o.example.com", "junk"], addedAt: Date())
        XCTAssertEqual(own.queryRelays(defaults: ["wss://d.example.com"]).map(\.description), ["wss://o.example.com"])
    }
}
