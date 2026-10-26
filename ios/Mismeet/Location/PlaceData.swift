import Foundation

/// Place cells for the names of spec section 8.7, from Overpass and cached in the caches directory.
actor PlaceData {
    private let directory: URL
    private var cells: [PlaceCell: [Place]] = [:]
    private var failedAt: [PlaceCell: Date] = [:]

    init() {
        directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("places", isDirectory: true)
    }

    func name(at point: LatLon, now: Date) async -> String? {
        var places: [Place] = []
        for cell in PlaceCell.around(point) {
            guard let loaded = await load(cell, now: now) else { return nil }
            places += loaded
        }
        return PlaceNames.name(at: point, places: places)
    }

    private func load(_ cell: PlaceCell, now: Date) async -> [Place]? {
        if let places = cells[cell] { return places }
        if let failed = failedAt[cell], now.timeIntervalSince(failed) < ProtocolConstants.rateLimitBackoffSeconds { return nil }
        let file = directory.appendingPathComponent("\(cell.row)_\(cell.col).json")
        var places: [Place]?
        if let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate,
           now.timeIntervalSince(modified) < ProtocolConstants.placeCellCacheSeconds,
           let data = try? Data(contentsOf: file) {
            places = try? PlaceNames.parse(data)
        }
        if places == nil, let data = await Overpass.fetch(PlaceNames.query(cell)), let parsed = try? PlaceNames.parse(data) {
            try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
            places = parsed
        }
        guard let places else {
            // A lookup cancelled because the contacts changed is no failure of Overpass.
            if !Task.isCancelled { failedAt[cell] = now }
            return nil
        }
        cells[cell] = places
        return places
    }
}
