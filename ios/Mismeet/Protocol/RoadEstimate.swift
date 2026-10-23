import Foundation

struct LatLon: Equatable, Hashable {
    var latitude: Double
    var longitude: Double
}

enum TravelMode: Equatable {
    case foot, bike, car

    init(speed: Double) {
        if speed >= ProtocolConstants.carSpeedFrom {
            self = .car
        } else if speed >= ProtocolConstants.bikeSpeedFrom {
            self = .bike
        } else {
            self = .foot
        }
    }
}

/// A grid cell of road data, spec section 8.6.
struct RoadCell: Hashable {
    var row: Int
    var col: Int

    init(row: Int, col: Int) {
        self.row = row
        self.col = col
    }

    init(_ point: LatLon) {
        row = Int((point.latitude * 100).rounded(.down))
        col = Int((point.longitude * 50).rounded(.down))
    }

    var bbox: String {
        String(format: "%.2f,%.2f,%.2f,%.2f", locale: Locale(identifier: "en_US_POSIX"),
               Double(row) / 100, Double(col) / 50, Double(row + 1) / 100, Double(col + 1) / 50)
    }
}

struct RoadWay: Equatable {
    var id: Int64
    var tags: [String: String]
    var points: [LatLon]

    private static let mainRoads = ["primary", "secondary", "tertiary"].flatMap { [$0, "\($0)_link"] }
    private static let carWays = Set(["motorway", "motorway_link", "trunk", "trunk_link", "unclassified", "residential", "living_street", "road"] + mainRoads)
    private static let bikeWays = Set(["cycleway", "path", "track", "living_street", "residential", "unclassified", "road"] + mainRoads)
    private static let footWays = Set(["footway", "pedestrian", "path", "track", "cycleway", "living_street", "residential", "unclassified", "road"] + mainRoads)

    func allows(_ mode: TravelMode, forward: Bool) -> Bool {
        guard let highway = tags["highway"] else { return false }
        switch mode {
        case .car:
            guard Self.carWays.contains(highway) else { return false }
            if tags["oneway"] == "-1" { return !forward }
            if ["yes", "true", "1"].contains(tags["oneway"]) || ["roundabout", "circular"].contains(tags["junction"]) { return forward }
            return true
        case .bike:
            guard tags["bicycle"] != "no" else { return false }
            return Self.bikeWays.contains(highway)
                || (["footway", "pedestrian"].contains(highway) && ["yes", "designated"].contains(tags["bicycle"]))
        case .foot:
            return tags["foot"] != "no" && Self.footWays.contains(highway)
        }
    }
}

enum Overpass {
    static let url = URL(string: "https://overpass-api.de/api/interpreter")!
    private static let highways = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|"
        + "tertiary|tertiary_link|unclassified|residential|living_street|road|cycleway|path|track|footway|pedestrian"
    private static let keptTags: Set = ["highway", "oneway", "junction", "bicycle", "foot"]

    static func query(_ cell: RoadCell) -> String {
        "[out:json][timeout:25];\nway[\"highway\"~\"^(\(highways))$\"](\(cell.bbox));\nout tags geom;"
    }

    static func parse(_ data: Data) throws -> [RoadWay] {
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let elements = root?["elements"] as? [[String: Any]] ?? []
        return elements.compactMap { way in
            guard way["type"] as? String == "way", let id = (way["id"] as? NSNumber)?.int64Value else { return nil }
            let tags = (way["tags"] as? [String: Any] ?? [:]).reduce(into: [String: String]()) { result, entry in
                if keptTags.contains(entry.key), let value = entry.value as? String { result[entry.key] = value }
            }
            let points = (way["geometry"] as? [[String: Any]] ?? []).compactMap { point -> LatLon? in
                guard let lat = (point["lat"] as? NSNumber)?.doubleValue, let lon = (point["lon"] as? NSNumber)?.doubleValue else { return nil }
                return LatLon(latitude: lat, longitude: lon)
            }
            return points.count < 2 ? nil : RoadWay(id: id, tags: tags, points: points)
        }
    }
}

/// The ways of the loaded cells, joined where they share a coordinate.
struct RoadNetwork {
    struct Node: Hashable {
        var lat: Int64
        var lon: Int64

        init(_ point: LatLon) {
            lat = Int64((point.latitude * 1e7).rounded())
            lon = Int64((point.longitude * 1e7).rounded())
        }
    }

    struct Segment {
        var way: RoadWay
        var from: LatLon
        var to: LatLon
        var forward: Bool
        var fromNode: Node { Node(from) }
        var toNode: Node { Node(to) }
    }

    let segments: [Segment]
    let leaving: [Node: [Segment]]

    init(_ ways: [RoadWay]) {
        var seen = Set<Int64>()
        var segments: [Segment] = []
        for way in ways where seen.insert(way.id).inserted {
            for (a, b) in zip(way.points, way.points.dropFirst()) where a != b {
                segments.append(Segment(way: way, from: a, to: b, forward: true))
                segments.append(Segment(way: way, from: b, to: a, forward: false))
            }
        }
        self.segments = segments
        leaving = Dictionary(grouping: segments, by: \.fromNode)
    }
}

enum EstimateResult: Equatable {
    case estimate(mode: TravelMode, path: [LatLon], point: LatLon)
    case needsCell(RoadCell)
    case none
}

/// Spec section 8.6. A cell that is neither loaded nor unavailable comes back as `.needsCell`;
/// the caller loads it and asks again, and counts the loads against the limit per estimate.
enum RoadEstimator {
    private static let maxSteps = 10_000

    static func estimate(fix: LatLon, speed: Double?, heading: Int?, ageSeconds: Int64, network: RoadNetwork,
                         loaded: Set<RoadCell>, unavailable: Set<RoadCell> = []) -> EstimateResult {
        guard let speed, let heading, speed >= ProtocolConstants.estimateMinSpeed,
              ageSeconds <= ProtocolConstants.estimateMaxFixAgeSeconds else { return .none }
        let length = speed * Double(min(max(ageSeconds, 0), ProtocolConstants.estimateSpanSeconds))
        guard length > 0 else { return .none }
        let mode = TravelMode(speed: speed)
        let plane = Plane(origin: fix)
        if let missing = startCells(plane).first(where: { !loaded.contains($0) && !unavailable.contains($0) }) {
            return .needsCell(missing)
        }

        var start: (segment: RoadNetwork.Segment, snap: (x: Double, y: Double), distance: Double, turn: Double)?
        for segment in network.segments where segment.way.allows(mode, forward: segment.forward) {
            let turn = angle(plane.bearing(segment.from, segment.to), Double(heading))
            guard turn <= ProtocolConstants.roadSnapBearingDegrees else { continue }
            let snap = plane.closest(segment.from, segment.to)
            let distance = hypot(snap.x, snap.y)
            guard distance <= ProtocolConstants.roadSnapMetres else { continue }
            if let best = start {
                if distance != best.distance {
                    if distance > best.distance { continue }
                } else if turn != best.turn {
                    if turn > best.turn { continue }
                } else if segment.way.id >= best.segment.way.id {
                    continue
                }
            }
            start = (segment, snap, distance, turn)
        }
        guard let start else { return .none }

        var segment = start.segment
        var position = start.snap
        var remaining = length
        var path = [plane.point(position.x, position.y)]
        for _ in 0..<maxSteps {
            let end = plane.xy(segment.to)
            let left = hypot(end.x - position.x, end.y - position.y)
            if remaining <= left {
                let t = remaining / left
                let stop = plane.point(position.x + (end.x - position.x) * t, position.y + (end.y - position.y) * t)
                return .estimate(mode: mode, path: path + [stop], point: stop)
            }
            remaining -= left
            position = end
            path.append(segment.to)
            let cell = RoadCell(segment.to)
            if !loaded.contains(cell) {
                return unavailable.contains(cell) ? .estimate(mode: mode, path: path, point: segment.to) : .needsCell(cell)
            }
            let bearing = plane.bearing(segment.from, segment.to)
            let current = segment
            var next: (segment: RoadNetwork.Segment, turn: Double)?
            for candidate in network.leaving[current.toNode] ?? [] {
                guard candidate.way.allows(mode, forward: candidate.forward), candidate.toNode != current.fromNode else { continue }
                let turn = angle(plane.bearing(candidate.from, candidate.to), bearing)
                if let best = next, !isBetter(candidate, turn, than: best, continuing: current.way.id) { continue }
                next = (candidate, turn)
            }
            guard let next else { return .estimate(mode: mode, path: path, point: current.to) }
            segment = next.segment
        }
        return .estimate(mode: mode, path: path, point: path[path.count - 1])
    }

    /// The cells of the fix and of the points one snap distance north, south, east and west of it.
    static func startCells(_ fix: LatLon) -> [RoadCell] {
        startCells(Plane(origin: fix))
    }

    private static func startCells(_ plane: Plane) -> [RoadCell] {
        let d = ProtocolConstants.roadSnapMetres
        var cells: [RoadCell] = []
        for (x, y) in [(0.0, 0.0), (0, d), (0, -d), (d, 0), (-d, 0)] {
            let cell = RoadCell(plane.point(x, y))
            if !cells.contains(cell) { cells.append(cell) }
        }
        return cells
    }

    private static func isBetter(_ candidate: RoadNetwork.Segment, _ turn: Double,
                                 than best: (segment: RoadNetwork.Segment, turn: Double), continuing wayId: Int64) -> Bool {
        if turn != best.turn { return turn < best.turn }
        let same = candidate.way.id == wayId
        if same != (best.segment.way.id == wayId) { return same }
        return candidate.way.id < best.segment.way.id
    }

    private static func angle(_ a: Double, _ b: Double) -> Double {
        abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180)
    }

    private struct Plane {
        static let metresPerLat = 110_574.0
        let origin: LatLon
        let metresPerLon: Double

        init(origin: LatLon) {
            self.origin = origin
            metresPerLon = cos(origin.latitude * .pi / 180) * 111_320
        }

        func xy(_ point: LatLon) -> (x: Double, y: Double) {
            ((point.longitude - origin.longitude) * metresPerLon, (point.latitude - origin.latitude) * Self.metresPerLat)
        }

        func point(_ x: Double, _ y: Double) -> LatLon {
            LatLon(latitude: origin.latitude + y / Self.metresPerLat, longitude: origin.longitude + x / metresPerLon)
        }

        func bearing(_ from: LatLon, _ to: LatLon) -> Double {
            let a = xy(from)
            let b = xy(to)
            return (atan2(b.x - a.x, b.y - a.y) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
        }

        /// The point of the segment closest to the origin, which is the fix.
        func closest(_ from: LatLon, _ to: LatLon) -> (x: Double, y: Double) {
            let a = xy(from)
            let b = xy(to)
            let dx = b.x - a.x
            let dy = b.y - a.y
            let t = min(max((-a.x * dx - a.y * dy) / (dx * dx + dy * dy), 0), 1)
            return (a.x + dx * t, a.y + dy * t)
        }
    }
}
