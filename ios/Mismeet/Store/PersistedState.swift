import Foundation

struct PersistedState: Codable, Equatable {
    static let defaultRelays = ["wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net"]

    var contacts: [Contact] = []
    var relays = PersistedState.defaultRelays
    var lastPublishedCreatedAt: UInt64?
    var lastPublishedAt: Date?
    var lastFix: LocationFix?
    var relayListPublishedAt: Date?
}

struct StateStore {
    private let url: URL

    init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Mismeet", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        url = directory.appendingPathComponent("state.json")
    }

    func load() -> PersistedState {
        guard let data = try? Data(contentsOf: url),
              let state = try? JSONDecoder().decode(PersistedState.self, from: data) else {
            return PersistedState()
        }
        return state
    }

    func save(_ state: PersistedState) {
        guard let data = try? JSONEncoder().encode(state) else { return }
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
