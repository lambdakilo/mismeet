import Foundation

/// Road cells for the estimates of spec section 8.6, from Overpass and cached in the caches directory.
actor RoadData {
    private static let maxCellsInMemory = 48
    private let directory: URL
    private var cells: [RoadCell: [RoadWay]] = [:]
    private var order: [RoadCell] = []
    private var failedAt: [RoadCell: Date] = [:]
    private var network = RoadNetwork([])

    init() {
        directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("roads", isDirectory: true)
    }

    func estimate(_ payload: LocationPayload, now: Date) async -> (mode: TravelMode, path: [LatLon], point: LatLon)? {
        let fix = LatLon(latitude: payload.latitude, longitude: payload.longitude)
        let age = Int64(now.timeIntervalSince1970) - Int64(payload.fixTime)
        var skipped = Set<RoadCell>()
        var loads = 0
        while true {
            let backedOff = failedAt.filter { now.timeIntervalSince($0.value) < ProtocolConstants.rateLimitBackoffSeconds }.keys
            let result = RoadEstimator.estimate(fix: fix, speed: payload.speed, heading: payload.course, ageSeconds: age,
                                                network: network, loaded: Set(cells.keys), unavailable: skipped.union(backedOff))
            switch result {
            case .estimate(let mode, let path, let point):
                return (mode, path, point)
            case .none:
                return nil
            case .needsCell(let cell):
                if loads < ProtocolConstants.roadCellsPerEstimate, await load(cell, now: now) {
                    loads += 1
                } else {
                    skipped.insert(cell)
                }
            }
        }
    }

    private func load(_ cell: RoadCell, now: Date) async -> Bool {
        let file = directory.appendingPathComponent("\(cell.row)_\(cell.col).json")
        var ways: [RoadWay]?
        if let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate,
           now.timeIntervalSince(modified) < ProtocolConstants.roadCellCacheSeconds,
           let data = try? Data(contentsOf: file) {
            ways = try? Overpass.parse(data)
        }
        if ways == nil, let data = await fetch(cell), let parsed = try? Overpass.parse(data) {
            try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
            ways = parsed
        }
        guard let ways else {
            failedAt[cell] = now
            return false
        }
        cells[cell] = ways
        order.removeAll { $0 == cell }
        order.append(cell)
        while order.count > Self.maxCellsInMemory {
            cells[order.removeFirst()] = nil
        }
        network = RoadNetwork(cells.values.flatMap { $0 })
        return true
    }

    private func fetch(_ cell: RoadCell) async -> Data? {
        var request = URLRequest(url: Overpass.url, timeoutInterval: 90)
        request.httpMethod = "POST"
        request.setValue("app.mismeet.ios (https://github.com/lambdakilo/mismeet)", forHTTPHeaderField: "User-Agent")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let query = Overpass.query(cell).addingPercentEncoding(withAllowedCharacters: allowed) ?? ""
        request.httpBody = Data("data=\(query)".utf8)
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return data
    }
}
