import XCTest
@testable import Mismeet

final class CountriesTests: XCTestCase {
    private static let helsinki = LatLon(latitude: 60.17, longitude: 24.94)
    private static let tallinn = LatLon(latitude: 59.437, longitude: 24.754)
    private static let stockholm = LatLon(latitude: 59.33, longitude: 18.07)
    private static let balticSea = LatLon(latitude: 58.5, longitude: 20.0)

    private var countries: Countries!

    override func setUpWithError() throws {
        countries = try XCTUnwrap(Countries.bundled())
    }

    func testFindsTheCountryOfAPoint() {
        XCTAssertEqual(countries.code(Self.helsinki), "FI")
        XCTAssertEqual(countries.code(Self.tallinn), "EE")
        XCTAssertEqual(countries.code(Self.stockholm), "SE")
        XCTAssertEqual(countries.code(LatLon(latitude: -26.20, longitude: 28.04)), "ZA")
        XCTAssertNil(countries.code(Self.balticSea))
    }

    func testLeavesOutAHoleInAnotherCountry() {
        XCTAssertEqual(countries.code(LatLon(latitude: -29.31, longitude: 27.48)), "LS")
    }

    func testOpensOnThisPhonesCountryOrElseWhereMostContactsAre() {
        XCTAssertEqual(countries.home(own: Self.tallinn, contacts: [Self.helsinki, Self.helsinki]), "EE")
        XCTAssertEqual(countries.home(own: nil, contacts: [Self.stockholm, Self.helsinki, Self.helsinki]), "FI")
        XCTAssertEqual(countries.home(own: Self.balticSea, contacts: [Self.stockholm, Self.helsinki, Self.helsinki]), "FI")
        XCTAssertEqual(countries.home(own: nil, contacts: [Self.stockholm, Self.helsinki, Self.helsinki, Self.stockholm]), "SE")
        XCTAssertEqual(countries.home(own: nil, contacts: [Self.helsinki, Self.stockholm]), "FI")
        XCTAssertNil(countries.home(own: nil, contacts: [Self.balticSea]))
    }
}
