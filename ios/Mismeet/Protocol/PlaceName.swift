import Foundation

/// A grid cell of place names, spec section 8.7.
struct PlaceCell: Hashable {
    var row: Int
    var col: Int

    init(row: Int, col: Int) {
        self.row = row
        self.col = col
    }

    init(_ point: LatLon) {
        row = Int((point.latitude * 10).rounded(.down))
        col = Int((point.longitude * 5).rounded(.down))
    }

    var bbox: String {
        String(format: "%.1f,%.1f,%.1f,%.1f", locale: Locale(identifier: "en_US_POSIX"),
               Double(row) / 10, Double(col) / 5, Double(row + 1) / 10, Double(col + 1) / 5)
    }

    /// The cells that the box from 5 km south-west to 5 km north-east of the position touches.
    static func around(_ point: LatLon) -> [PlaceCell] {
        let dLat = ProtocolConstants.placeNameMetres / PlaceNames.metresPerLat
        let dLon = ProtocolConstants.placeNameMetres / PlaceNames.metresPerLon(at: point.latitude)
        let low = PlaceCell(LatLon(latitude: point.latitude - dLat, longitude: point.longitude - dLon))
        let high = PlaceCell(LatLon(latitude: point.latitude + dLat, longitude: point.longitude + dLon))
        return (low.row...high.row).flatMap { row in (low.col...high.col).map { PlaceCell(row: row, col: $0) } }
    }
}

struct Place: Equatable {
    var id: Int64
    var place: String
    var name: String
    var point: LatLon
}

enum PlaceNames {
    static let metresPerLat = 110_574.0

    static func metresPerLon(at latitude: Double) -> Double {
        cos(latitude * .pi / 180) * 111_320
    }

    static func query(_ cell: PlaceCell) -> String {
        "[out:json][timeout:25];\nnode[\"place\"~\"^(suburb|town|village|city)$\"][\"name\"](\(cell.bbox));\nout;"
    }

    static func parse(_ data: Data) throws -> [Place] {
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let elements = root?["elements"] as? [[String: Any]] ?? []
        return elements.compactMap { node in
            guard node["type"] as? String == "node", let id = (node["id"] as? NSNumber)?.int64Value,
                  let lat = (node["lat"] as? NSNumber)?.doubleValue, let lon = (node["lon"] as? NSNumber)?.doubleValue,
                  let tags = node["tags"] as? [String: Any], let place = tags["place"] as? String,
                  let name = tags["name"] as? String else { return nil }
            return Place(id: id, place: place, name: name, point: LatLon(latitude: lat, longitude: lon))
        }
    }

    /// The name of a position from the places of the cells around it, nil when none is near.
    static func name(at point: LatLon, places: [Place]) -> String? {
        let metresPerLon = metresPerLon(at: point.latitude)
        func distance(_ place: Place) -> Double {
            hypot((place.point.longitude - point.longitude) * metresPerLon, (place.point.latitude - point.latitude) * metresPerLat)
        }
        func nearest(_ kinds: Set<String>) -> Place? {
            let near: [(place: Place, metres: Double)] = places
                .filter { kinds.contains($0.place) }
                .map { ($0, distance($0)) }
                .filter { $0.metres <= ProtocolConstants.placeNameMetres }
            return near.min { a, b in a.metres != b.metres ? a.metres < b.metres : a.place.id < b.place.id }?.place
        }
        return (nearest(["suburb", "town", "village"]) ?? nearest(["city"]))?.name
    }
}
