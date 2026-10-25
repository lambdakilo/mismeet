import Foundation

/// Which country a point lies in, from the Natural Earth borders that scripts/make-countries.py
/// writes into the app bundle, so no position leaves the phone. Borders are simplified to about
/// 2 km, so a point that close to one may land in the neighbour, and a point off the simplified
/// coast, such as central Stockholm, takes the nearest country within `coastDegrees`.
struct Countries: Sendable {
    private struct Polygon: Sendable {
        let code: String
        let rings: [[Double]]
        let minLatitude: Double
        let maxLatitude: Double
        let minLongitude: Double
        let maxLongitude: Double

        init(code: String, rings: [[Double]]) {
            self.code = code
            self.rings = rings
            let outer = rings[0]
            let longitudes = stride(from: 0, to: outer.count, by: 2).map { outer[$0] }
            let latitudes = stride(from: 1, to: outer.count, by: 2).map { outer[$0] }
            minLongitude = longitudes.min() ?? 0
            maxLongitude = longitudes.max() ?? 0
            minLatitude = latitudes.min() ?? 0
            maxLatitude = latitudes.max() ?? 0
        }

        /// Even-odd ray casting over every ring, so a hole such as Lesotho inside South Africa is outside.
        func contains(_ point: LatLon) -> Bool {
            guard (minLatitude...maxLatitude).contains(point.latitude),
                  (minLongitude...maxLongitude).contains(point.longitude) else { return false }
            var inside = false
            for ring in rings {
                var j = ring.count - 2
                for i in stride(from: 0, to: ring.count, by: 2) {
                    let (x1, y1, x2, y2) = (ring[i], ring[i + 1], ring[j], ring[j + 1])
                    if (y1 > point.latitude) != (y2 > point.latitude),
                       point.longitude < (x2 - x1) * (point.latitude - y1) / (y2 - y1) + x1 {
                        inside.toggle()
                    }
                    j = i
                }
            }
            return inside
        }

        /// Distance to the outer border in degrees of latitude, or nil when it is beyond `coastDegrees`.
        func coastDistance(_ point: LatLon) -> Double? {
            let scale = max(cos(point.latitude * .pi / 180), 0.01)
            let longitudeLimit = Countries.coastDegrees / scale
            guard (minLatitude - Countries.coastDegrees...maxLatitude + Countries.coastDegrees).contains(point.latitude),
                  (minLongitude - longitudeLimit...maxLongitude + longitudeLimit).contains(point.longitude) else { return nil }
            let outer = rings[0]
            var best = Double.greatestFiniteMagnitude
            for i in stride(from: 0, to: outer.count - 2, by: 2) {
                let ax = (outer[i] - point.longitude) * scale
                let ay = outer[i + 1] - point.latitude
                let bx = (outer[i + 2] - point.longitude) * scale
                let by = outer[i + 3] - point.latitude
                let length = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
                let t = length == 0 ? 0 : min(max(-(ax * (bx - ax) + ay * (by - ay)) / length, 0), 1)
                best = min(best, hypot(ax + t * (bx - ax), ay + t * (by - ay)))
            }
            return best <= Countries.coastDegrees ? best : nil
        }
    }

    /// About 20 km.
    static let coastDegrees = 0.2

    private let polygons: [Polygon]

    init(json: Data) throws {
        let countries = try JSONDecoder().decode([String: [[[Double]]]].self, from: json)
        polygons = countries.sorted { $0.key < $1.key }.flatMap { code, polygons in
            polygons.map { Polygon(code: code, rings: $0) }
        }
    }

    static func bundled() -> Countries? {
        guard let url = Bundle.main.url(forResource: "countries", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? Countries(json: data)
    }

    /// The ISO 3166-1 alpha-2 code, or Natural Earth's own three letters for a place without one.
    func code(_ point: LatLon) -> String? {
        if let polygon = polygons.first(where: { $0.contains(point) }) { return polygon.code }
        return polygons
            .compactMap { polygon in polygon.coastDistance(point).map { (polygon.code, $0) } }
            .min { $0.1 < $1.1 }?.0
    }

    /// The country the map opens on: the one this phone is in, or else the one holding the most
    /// contacts, the first of them on a tie.
    func home(own: LatLon?, contacts: [LatLon]) -> String? {
        if let own, let code = code(own) { return code }
        let codes = contacts.compactMap(code)
        let counts = Dictionary(codes.map { ($0, 1) }, uniquingKeysWith: +)
        return codes.first { counts[$0] == counts.values.max() }
    }
}
