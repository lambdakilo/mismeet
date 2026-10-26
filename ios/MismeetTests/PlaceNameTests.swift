import XCTest
@testable import Mismeet

final class PlaceNameTests: XCTestCase {
    // University of Helsinki's Kumpula campus, in cell (602, 124).
    private let campus = LatLon(latitude: 60.2045, longitude: 24.9625)
    private var metresPerLon: Double { cos(campus.latitude * .pi / 180) * 111_320 }

    private func place(_ id: Int64, _ place: String, _ name: String, _ x: Double, _ y: Double) -> Place {
        Place(id: id, place: place, name: name,
              point: LatLon(latitude: campus.latitude + y / 110_574, longitude: campus.longitude + x / metresPerLon))
    }

    func testCellAndQuery() {
        XCTAssertEqual(PlaceCell(campus), PlaceCell(row: 602, col: 124))
        XCTAssertEqual(
            PlaceNames.query(PlaceCell(campus)),
            "[out:json][timeout:25];\nnode[\"place\"~\"^(suburb|town|village|city)$\"][\"name\"](60.2,24.8,60.3,25.0);\nout;"
        )
        XCTAssertEqual(PlaceCell(LatLon(latitude: -33.87, longitude: 151.21)).bbox, "-33.9,151.2,-33.8,151.4")
    }

    func testLoadsTheCellsFiveKilometresAround() {
        XCTAssertEqual(PlaceCell.around(campus), [
            PlaceCell(row: 601, col: 124), PlaceCell(row: 601, col: 125),
            PlaceCell(row: 602, col: 124), PlaceCell(row: 602, col: 125),
        ])
        XCTAssertEqual(PlaceCell.around(LatLon(latitude: 60.25, longitude: 24.9)), [PlaceCell(row: 602, col: 124)])
    }

    func testParseKeepsNamedPlaceNodes() throws {
        let json = """
        {"elements":[
          {"type":"node","id":42,"lat":60.2093602,"lon":24.9646243,"tags":{"name":"Kumpula","name:sv":"Gumtäkt","place":"suburb"}},
          {"type":"node","id":43,"lat":60.2,"lon":24.9,"tags":{"place":"village"}},
          {"type":"way","id":44,"tags":{"name":"Kumpula","place":"suburb"}}
        ]}
        """
        XCTAssertEqual(try PlaceNames.parse(Data(json.utf8)), [
            Place(id: 42, place: "suburb", name: "Kumpula", point: LatLon(latitude: 60.2093602, longitude: 24.9646243)),
        ])
    }

    func testNamesTheDistrictRatherThanTheCity() {
        let places = [
            place(1, "city", "Helsinki", 300, 0),
            place(2, "suburb", "Kumpula", 0, 800),
            place(3, "neighbourhood", "Kumpulan kampus", 0, 90),
        ]
        XCTAssertEqual(PlaceNames.name(at: campus, places: places), "Kumpula")
    }

    func testTownAndVillageCountAsDistricts() {
        XCTAssertEqual(PlaceNames.name(at: campus, places: [place(1, "suburb", "Far", 2_000, 0), place(2, "town", "Near", 0, -1_500)]), "Near")
        XCTAssertEqual(PlaceNames.name(at: campus, places: [place(1, "village", "Kylä", 4_900, 0), place(2, "city", "Kaupunki", 100, 0)]), "Kylä")
    }

    func testFallsBackToTheCityAndThenToNothing() {
        XCTAssertEqual(PlaceNames.name(at: campus, places: [place(1, "suburb", "Far", 5_100, 0), place(2, "city", "Helsinki", 0, 3_000)]), "Helsinki")
        XCTAssertNil(PlaceNames.name(at: campus, places: [place(1, "suburb", "Far", 5_100, 0), place(2, "city", "Helsinki", 0, -5_100)]))
        XCTAssertNil(PlaceNames.name(at: campus, places: []))
    }

    func testTieGoesToTheLowerId() {
        XCTAssertEqual(PlaceNames.name(at: campus, places: [place(7, "suburb", "East", 1_000, 0), place(5, "suburb", "West", -1_000, 0)]), "West")
    }

    func testKumpulaCampusFromOpenStreetMap() {
        let places = [
            Place(id: 1, place: "suburb", name: "Kumpula", point: LatLon(latitude: 60.2093602, longitude: 24.9646243)),
            Place(id: 2, place: "suburb", name: "Toukola", point: LatLon(latitude: 60.2084442, longitude: 24.9721468)),
            Place(id: 3, place: "suburb", name: "Hermanni", point: LatLon(latitude: 60.1967824, longitude: 24.9672281)),
            Place(id: 4, place: "suburb", name: "Vallila", point: LatLon(latitude: 60.1944516, longitude: 24.9564189)),
            Place(id: 5, place: "city", name: "Helsinki", point: LatLon(latitude: 60.1674, longitude: 24.9426)),
        ]
        XCTAssertEqual(PlaceNames.name(at: campus, places: places), "Kumpula")
    }
}
