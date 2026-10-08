import CoreLocation
import XCTest
@testable import Mismeet

final class LocationFixTests: XCTestCase {
    func testMapsCoreLocationValuesToThePayload() {
        let fix = LocationFix(
            time: Date(timeIntervalSince1970: 1_759_840_101.7),
            latitude: 60.1698561, longitude: 24.9383794, horizontalAccuracy: 11.6,
            ellipsoidalAltitude: 20.6, speed: 1.44, course: 359.6
        )
        let payload = fix.payload(battery: 0.5)
        XCTAssertEqual(payload.fixTime, 1_759_840_101)
        XCTAssertEqual(payload.accuracy, 12)
        XCTAssertEqual(payload.altitude, 21)
        XCTAssertEqual(payload.speed, 1.44)
        XCTAssertEqual(payload.course, 0)
        XCTAssertEqual(payload.battery, 0.5)
        XCTAssertEqual(payload.encode(), "{\"v\":1,\"ts\":1759840101,\"lat\":60.169856,\"lon\":24.938379,\"acc\":12,\"alt\":21,\"spd\":1.4,\"hdg\":0,\"bat\":0.5}")
    }

    func testDropsUnknownValuesAndInvalidFixes() {
        let valid = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 1, longitude: 2), altitude: 5,
            horizontalAccuracy: 30, verticalAccuracy: -1, course: -1, speed: -1, timestamp: Date()
        )
        let fix = LocationFix(valid)
        XCTAssertNotNil(fix)
        XCTAssertNil(fix?.ellipsoidalAltitude)
        XCTAssertNil(fix?.speed)
        XCTAssertNil(fix?.course)
        XCTAssertEqual(fix?.payload(battery: nil).encode(), "{\"v\":1,\"ts\":\(fix!.payload(battery: nil).fixTime),\"lat\":1,\"lon\":2,\"acc\":30}")

        let invalid = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 1, longitude: 2), altitude: 5,
            horizontalAccuracy: -1, verticalAccuracy: -1, timestamp: Date()
        )
        XCTAssertNil(LocationFix(invalid))
    }
}
