import XCTest
import NostrSDK
@testable import Mismeet

final class RelayPlanTests: XCTestCase {
    func testEachRelayGetsOnlyItsOwnAuthors() throws {
        let alice = Keys.generate().publicKey()
        let bob = Keys.generate().publicKey()
        let one = try RelayUrl.parse(url: "wss://one.example.com")
        let two = try RelayUrl.parse(url: "wss://two.example.com")
        let plan = RelayPlan.authorsByRelay([
            ContactRelays(publicKey: alice, relays: [one, two]),
            ContactRelays(publicKey: bob, relays: [two, two]),
        ])
        XCTAssertEqual(plan.count, 2)
        XCTAssertEqual(plan[one], [alice])
        XCTAssertEqual(plan[two], [alice, bob])
    }

    func testCapsRelaysPerContact() throws {
        let alice = Keys.generate().publicKey()
        let relays = try (1...10).map { try RelayUrl.parse(url: "wss://relay\($0).example.com") }
        let plan = RelayPlan.authorsByRelay([ContactRelays(publicKey: alice, relays: relays)])
        XCTAssertEqual(plan.count, ProtocolConstants.maxRelaysPerContact)
        XCTAssertNil(plan[relays[9]])
    }
}
