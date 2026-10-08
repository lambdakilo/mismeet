import XCTest
@testable import Mismeet

final class LocationPayloadTests: XCTestCase {
    private let full = LocationPayload(
        fixTime: 1_759_840_101, latitude: 60.169856, longitude: 24.938379, accuracy: 12,
        altitude: 21, speed: 1.4, course: 270, battery: 0.73
    )

    func testEncodesAllKeysInSpecOrder() {
        XCTAssertEqual(
            full.encode(),
            "{\"v\":1,\"ts\":1759840101,\"lat\":60.169856,\"lon\":24.938379,\"acc\":12,\"alt\":21,\"spd\":1.4,\"hdg\":270,\"bat\":0.73}"
        )
    }

    func testOmitsUnknownOptionalKeys() {
        let payload = LocationPayload(fixTime: 1, latitude: 60.5, longitude: -24, accuracy: 0)
        XCTAssertEqual(payload.encode(), "{\"v\":1,\"ts\":1,\"lat\":60.5,\"lon\":-24,\"acc\":0}")
    }

    func testRoundsCoordinatesToSixDecimalsWithoutExponent() {
        let payload = LocationPayload(
            fixTime: 1, latitude: 60.1698561234, longitude: -24.9383794, accuracy: 3,
            speed: 1.44, battery: 0.731
        )
        XCTAssertEqual(payload.encode(), "{\"v\":1,\"ts\":1,\"lat\":60.169856,\"lon\":-24.938379,\"acc\":3,\"spd\":1.4,\"bat\":0.73}")
        XCTAssertEqual(JSONNumber.format(0.0000001, maxDecimals: 6), "0")
        XCTAssertEqual(JSONNumber.format(-0.0000001, maxDecimals: 6), "0")
        XCTAssertEqual(JSONNumber.format(123456789.5, maxDecimals: 1), "123456789.5")
    }

    func testDecodesWhatItEncodes() throws {
        XCTAssertEqual(try LocationPayload.decode(full.encode()), full)
    }

    func testIgnoresUnknownKeysAndInvalidOptionals() throws {
        let decoded = try LocationPayload.decode(
            "{\"v\":1,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3,\"future\":\"x\",\"alt\":\"high\",\"hdg\":400,\"bat\":2}"
        )
        XCTAssertEqual(decoded, LocationPayload(fixTime: 5, latitude: 1.5, longitude: 2.5, accuracy: 3))
    }

    func testRejectsWrongVersionAndMissingOrMistypedRequiredKeys() {
        XCTAssertThrowsError(try LocationPayload.decode("{\"v\":2,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .unsupportedVersion)
        }
        XCTAssertThrowsError(try LocationPayload.decode("{\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .unsupportedVersion)
        }
        XCTAssertThrowsError(try LocationPayload.decode("{\"v\":1,\"ts\":5,\"lat\":\"1.5\",\"lon\":2.5,\"acc\":3}")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .invalid("lat"))
        }
        XCTAssertThrowsError(try LocationPayload.decode("{\"v\":1,\"ts\":5,\"lat\":1.5,\"lon\":2.5}")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .invalid("acc"))
        }
        XCTAssertThrowsError(try LocationPayload.decode("{\"v\":true,\"ts\":5,\"lat\":1.5,\"lon\":2.5,\"acc\":3}")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .unsupportedVersion)
        }
        XCTAssertThrowsError(try LocationPayload.decode("[1]")) {
            XCTAssertEqual($0 as? LocationPayload.DecodeError, .notAnObject)
        }
    }
}
