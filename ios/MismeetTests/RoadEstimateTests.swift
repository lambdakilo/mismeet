import XCTest
@testable import Mismeet

final class RoadEstimateTests: XCTestCase {
    // The middle of cell (6016, 1246), about 550 m from each of its edges.
    private let origin = LatLon(latitude: 60.165, longitude: 24.93)
    private var metresPerLon: Double { cos(origin.latitude * .pi / 180) * 111_320 }

    private func at(_ x: Double, _ y: Double) -> LatLon {
        LatLon(latitude: origin.latitude + y / 110_574, longitude: origin.longitude + x / metresPerLon)
    }

    private func way(_ id: Int64, _ points: [(Double, Double)], tags: [String: String] = ["highway": "residential"]) -> RoadWay {
        RoadWay(id: id, tags: tags, points: points.map { at($0.0, $0.1) })
    }

    private func estimate(_ ways: [RoadWay], _ speed: Double?, _ heading: Int?, _ age: Int64, fix: LatLon? = nil,
                          loaded: Set<RoadCell>? = nil, unavailable: Set<RoadCell> = []) -> EstimateResult {
        let fix = fix ?? at(0, 5)
        let cells = loaded ?? Set(ways.flatMap(\.points).map { RoadCell($0) } + RoadEstimator.startCells(fix))
        return RoadEstimator.estimate(fix: fix, speed: speed, heading: heading, ageSeconds: age,
                                      network: RoadNetwork(ways), loaded: cells, unavailable: unavailable)
    }

    private func assertNear(_ expected: LatLon, _ actual: LatLon?, file: StaticString = #filePath, line: UInt = #line) {
        guard let actual else { return XCTFail("no estimate", file: file, line: line) }
        let dx = (actual.longitude - expected.longitude) * metresPerLon
        let dy = (actual.latitude - expected.latitude) * 110_574
        XCTAssertLessThan(hypot(dx, dy), 1.0, file: file, line: line)
    }

    private func point(_ result: EstimateResult) -> LatLon? {
        if case .estimate(_, _, let point) = result { return point }
        return nil
    }

    func testModeFollowsSpeed() {
        XCTAssertEqual(TravelMode(speed: 1.0), .foot)
        XCTAssertEqual(TravelMode(speed: 2.49), .foot)
        XCTAssertEqual(TravelMode(speed: 2.5), .bike)
        XCTAssertEqual(TravelMode(speed: 8.49), .bike)
        XCTAssertEqual(TravelMode(speed: 8.5), .car)
    }

    func testNoEstimateWhenSlowWithoutHeadingOrOld() {
        let road = [way(1, [(-100, 0), (2000, 0)])]
        XCTAssertEqual(estimate(road, 0.9, 90, 60), .none)
        XCTAssertEqual(estimate(road, 10, nil, 60), .none)
        XCTAssertEqual(estimate(road, nil, 90, 60), .none)
        XCTAssertEqual(estimate(road, 10, 90, 901), .none)
    }

    func testFollowsStraightRoad() {
        guard case .estimate(let mode, let path, let point) = estimate([way(1, [(-100, 0), (2000, 0)])], 10, 90, 60) else {
            return XCTFail("no estimate")
        }
        XCTAssertEqual(mode, .car)
        assertNear(at(0, 0), path.first)
        assertNear(at(600, 0), point)
    }

    func testSpanStopsAtFiveMinutes() {
        assertNear(at(1500, 0), point(estimate([way(1, [(-100, 0), (2000, 0)])], 5, 90, 600)))
    }

    func testNoEstimateWhenHeadingPointsAwayFromEveryRoad() {
        XCTAssertEqual(estimate([way(1, [(-100, 0), (2000, 0)])], 10, 0, 60), .none)
    }

    func testTakesTheLeastTurnAtAJunction() {
        let ways = [
            way(1, [(-100, 0), (300, 0)]),
            way(2, [(300, 0), (300, -300)]),
            way(3, [(300, 0), (600, 100)]),
        ]
        let along = 300 / hypot(300.0, 100.0)
        assertNear(at(300 + 300 * along, 100 * along), point(estimate(ways, 10, 90, 60)))
    }

    func testStopsAtADeadEnd() {
        assertNear(at(200, 0), point(estimate([way(1, [(-100, 0), (200, 0)])], 10, 90, 60)))
    }

    func testCarKeepsToOneway() {
        let oneway = [way(1, [(500, 0), (-100, 0)], tags: ["highway": "residential", "oneway": "yes"])]
        XCTAssertEqual(estimate(oneway, 10, 90, 60), .none)
        assertNear(at(300, 0), point(estimate(oneway, 5, 90, 60)))
    }

    func testBikeUsesCycleTracksButNotMotorways() {
        XCTAssertEqual(estimate([way(1, [(-100, 0), (2000, 0)], tags: ["highway": "motorway"])], 5, 90, 60), .none)
        XCTAssertEqual(estimate([way(1, [(-100, 0), (2000, 0)], tags: ["highway": "footway"])], 5, 90, 60), .none)
        let shared = [way(1, [(-100, 0), (2000, 0)], tags: ["highway": "footway", "bicycle": "designated"])]
        assertNear(at(300, 0), point(estimate(shared, 5, 90, 60)))
    }

    func testAsksForTheNextCellAndStopsWhenItIsUnavailable() {
        let ways = [way(1, [(-100, 0), (800, 0)]), way(2, [(800, 0), (1500, 0)])]
        let home: Set = [RoadCell(row: 6016, col: 1246)]
        XCTAssertEqual(estimate(ways, 20, 90, 60, loaded: home), .needsCell(RoadCell(row: 6016, col: 1247)))
        let stopped = estimate(ways, 20, 90, 60, loaded: home, unavailable: [RoadCell(row: 6016, col: 1247)])
        assertNear(at(800, 0), point(stopped))
    }

    func testCellsAndQuery() {
        XCTAssertEqual(RoadCell(LatLon(latitude: 60.1699, longitude: 24.9384)), RoadCell(row: 6016, col: 1246))
        XCTAssertEqual(RoadCell(row: 6016, col: 1246).bbox, "60.16,24.92,60.17,24.94")
        XCTAssertTrue(Overpass.query(RoadCell(row: 6016, col: 1246)).contains("(60.16,24.92,60.17,24.94);\nout tags geom;"))
    }

    func testParsesOverpassWays() throws {
        let json = """
            {"version":0.6,"elements":[
              {"type":"way","id":42,"bounds":{},"geometry":[{"lat":60.1,"lon":24.9},{"lat":60.2,"lon":24.95}],
               "tags":{"highway":"cycleway","name":"Baana","foot":"designated"}},
              {"type":"way","id":43,"geometry":[{"lat":60.1,"lon":24.9}],"tags":{"highway":"path"}},
              {"type":"node","id":1,"lat":60.1,"lon":24.9}
            ]}
            """
        let ways = try Overpass.parse(Data(json.utf8))
        XCTAssertEqual(ways.count, 1)
        XCTAssertEqual(ways.first?.id, 42)
        XCTAssertEqual(ways.first?.tags, ["highway": "cycleway", "foot": "designated"])
        XCTAssertEqual(ways.first?.points, [LatLon(latitude: 60.1, longitude: 24.9), LatLon(latitude: 60.2, longitude: 24.95)])
    }
}
