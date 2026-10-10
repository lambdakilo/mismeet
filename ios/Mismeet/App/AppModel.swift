import CoreLocation
import Foundation
import NostrSDK
import Observation
import OSLog
import UIKit

struct LogLine: Identifiable, Equatable {
    let id = UUID()
    let time: Date
    let text: String
}

@MainActor
@Observable
final class AppModel {
    static let shared = AppModel()

    enum PublishTrigger {
        case wakeup, foreground, manual, contactsChanged
    }

    private(set) var state: PersistedState
    private(set) var log: [LogLine] = []
    private(set) var authorization: CLAuthorizationStatus
    private(set) var isRefreshing = false
    private(set) var isPublishing = false
    let keys: Keys
    let npub: String

    private let store = StateStore()
    private let location = LocationService()
    private let relays = RelayService()
    private let logger = Logger(subsystem: "app.mismeet.ios", category: "model")
    private var pendingTrigger: PublishTrigger?

    private init() {
        state = store.load()
        var startupProblem: String?
        do {
            keys = try KeyStore.loadOrCreate()
        } catch {
            keys = Keys.generate()
            startupProblem = "Keychain failed, using a temporary key: \(error)"
        }
        npub = (try? keys.publicKey().toBech32()) ?? keys.publicKey().toHex()
        authorization = location.authorizationStatus
        UIDevice.current.isBatteryMonitoringEnabled = true
        if let startupProblem { log(startupProblem) }
        location.onFix = { [weak self] fix in self?.handle(fix: fix) }
        location.onAuthorizationChange = { [weak self] status in
            self?.authorization = status
            self?.location.startMonitoringIfAuthorized()
        }
        location.startMonitoringIfAuthorized()
        if state.relayListPublishedAt == nil {
            Task { await publishRelayList() }
        }
        #if DEBUG
        // Test hook: `SIMCTL_CHILD_MISMEET_TEST_INVITES="uri;uri" xcrun simctl launch ...` adds contacts.
        if let invites = ProcessInfo.processInfo.environment["MISMEET_TEST_INVITES"] {
            for (index, text) in invites.split(separator: ";").enumerated() {
                try? addContact(invite: String(text), name: "Test \(index + 1)")
            }
        }
        #endif
        log("Invite: \(inviteURI)")
    }

    var inviteURI: String {
        let urls = state.relays.compactMap { try? RelayUrl.parse(url: $0) }
        return (try? Invite(publicKey: keys.publicKey(), relays: urls).uri()) ?? "nostr:\(npub)"
    }

    var authorizationText: String {
        switch authorization {
        case .authorizedAlways: "Always"
        case .authorizedWhenInUse: "While using the app"
        case .denied: "Denied"
        case .restricted: "Restricted"
        case .notDetermined: "Not asked yet"
        @unknown default: "Unknown"
        }
    }

    // MARK: Lifecycle

    func becameActive() {
        Task { await refresh() }
        if authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
            pendingTrigger = .foreground
            location.requestFreshFix()
        }
    }

    func requestLocationAuthorization() {
        location.requestAuthorization()
    }

    // MARK: Contacts

    func addContact(invite text: String, name: String) throws {
        let invite = try Invite.parse(text)
        let id = invite.publicKey.toHex()
        guard id != keys.publicKey().toHex() else { throw ContactError.ownKey }
        guard !state.contacts.contains(where: { $0.id == id }) else { throw ContactError.duplicate }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        state.contacts.append(Contact(
            id: id,
            name: trimmed.isEmpty ? String(npubPrefix(of: invite.publicKey)) : trimmed,
            relays: invite.relays.map(\.description),
            addedAt: Date()
        ))
        save()
        recipientsChanged()
        Task { await refresh() }
    }

    func removeContacts(at offsets: IndexSet) {
        state.contacts.remove(atOffsets: offsets)
        save()
        recipientsChanged()
    }

    func setShare(_ id: String, _ share: Bool) {
        guard let index = state.contacts.firstIndex(where: { $0.id == id }), state.contacts[index].share != share else { return }
        if share, state.contacts.filter(\.share).count >= ProtocolConstants.maxSharingContacts {
            log("Cannot share with more than \(ProtocolConstants.maxSharingContacts) contacts")
            return
        }
        state.contacts[index].share = share
        save()
        recipientsChanged()
    }

    enum ContactError: LocalizedError {
        case ownKey, duplicate

        var errorDescription: String? {
            switch self {
            case .ownKey: "That is your own key."
            case .duplicate: "That contact is already in the list."
            }
        }
    }

    // MARK: Relays

    func addRelay(_ text: String) {
        guard let url = try? RelayUrl.parse(url: text.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            log("Not a relay URL: \(text)")
            return
        }
        let value = url.description
        guard !state.relays.contains(value) else { return }
        guard state.relays.count < ProtocolConstants.maxRelaysPerList else {
            log("At most \(ProtocolConstants.maxRelaysPerList) relays")
            return
        }
        state.relays.append(value)
        save()
        Task { await publishRelayList() }
    }

    func removeRelays(at offsets: IndexSet) {
        state.relays.remove(atOffsets: offsets)
        save()
        Task { await publishRelayList() }
    }

    // MARK: Publishing

    func publishNow() {
        guard authorization == .authorizedAlways || authorization == .authorizedWhenInUse else {
            log("Location permission is needed to publish")
            return
        }
        pendingTrigger = .manual
        location.requestFreshFix()
    }

    private func handle(fix: LocationFix) {
        let trigger = pendingTrigger ?? .wakeup
        pendingTrigger = nil
        state.lastFix = fix
        let task = BackgroundTask(name: "publish")
        Task {
            await publish(fix: fix, trigger: trigger)
            task.end()
        }
    }

    private func publish(fix: LocationFix, trigger: PublishTrigger) async {
        if trigger == .wakeup || trigger == .foreground,
           let last = state.lastPublishedAt,
           Date().timeIntervalSince(last) < ProtocolConstants.publishThrottleSeconds {
            log("Skipped publish: published less than a minute ago")
            return
        }
        guard Date().timeIntervalSince(fix.time) <= ProtocolConstants.maxFixAgeSeconds else {
            log("Skipped publish: the fix is older than an hour")
            return
        }
        let recipients = sharingRecipients()
        guard !recipients.isEmpty else {
            log("Skipped publish: nobody to share with")
            return
        }
        do {
            let event = try LocationEventBuilder.build(
                payload: fix.payload(battery: batteryLevel()),
                recipients: recipients,
                keys: keys,
                now: UInt64(Date().timeIntervalSince1970),
                previousCreatedAt: state.lastPublishedCreatedAt
            )
            await send(event, describing: "Location")
        } catch {
            log("Publish failed: \(error)")
        }
    }

    /// Spec section 8.3: after a contact or share change, replace the event on the relays at once.
    private func recipientsChanged() {
        guard state.lastPublishedCreatedAt != nil else {
            if authorization == .authorizedAlways || authorization == .authorizedWhenInUse, !sharingRecipients().isEmpty {
                pendingTrigger = .contactsChanged
                location.requestFreshFix()
            }
            return
        }
        Task {
            let recipients = sharingRecipients()
            let now = UInt64(Date().timeIntervalSince1970)
            do {
                if let fix = state.lastFix,
                   Date().timeIntervalSince(fix.time) <= ProtocolConstants.maxFixAgeSeconds,
                   !recipients.isEmpty {
                    let event = try LocationEventBuilder.build(
                        payload: fix.payload(battery: batteryLevel()),
                        recipients: recipients,
                        keys: keys,
                        now: now,
                        previousCreatedAt: state.lastPublishedCreatedAt
                    )
                    await send(event, describing: "Location")
                } else {
                    let event = try LocationEventBuilder.buildRevocation(keys: keys, now: now, previousCreatedAt: state.lastPublishedCreatedAt)
                    await send(event, describing: recipients.isEmpty ? "Revocation" : "Placeholder")
                    if !recipients.isEmpty, authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
                        pendingTrigger = .contactsChanged
                        location.requestFreshFix()
                    }
                }
            } catch {
                log("Republish failed: \(error)")
            }
        }
    }

    private func publishRelayList() async {
        do {
            let event = try RelayList.event(relays: state.relays, keys: keys, now: UInt64(Date().timeIntervalSince1970))
            let result = await relays.publish(event: event, relayUrls: state.relays)
            if result.succeeded.isEmpty {
                log("Relay list: no relay accepted it, \(result.summary)")
            } else {
                state.relayListPublishedAt = Date()
                save()
                log("Relay list: accepted by \(result.succeeded.count) of \(state.relays.count) relays")
            }
        } catch {
            log("Relay list failed: \(error)")
        }
    }

    private func send(_ event: Event, describing what: String) async {
        isPublishing = true
        defer { isPublishing = false }
        let result = await relays.publish(event: event, relayUrls: state.relays)
        if result.succeeded.isEmpty {
            log("\(what): no relay accepted it, \(result.summary)")
        } else {
            state.lastPublishedCreatedAt = event.createdAt().asSecs()
            state.lastPublishedAt = Date()
            save()
            log("\(what): accepted by \(result.succeeded.count) of \(state.relays.count) relays")
        }
    }

    private func sharingRecipients() -> [PublicKey] {
        state.contacts.filter(\.share).compactMap(\.publicKey)
    }

    private func batteryLevel() -> Double? {
        let level = UIDevice.current.batteryLevel
        return level >= 0 ? Double(level) : nil
    }

    // MARK: Reading

    func refresh() async {
        guard !isRefreshing, !state.contacts.isEmpty else { return }
        isRefreshing = true
        defer { isRefreshing = false }

        let now = Date()
        let due = Set(state.contacts.filter { contact in
            contact.relayListCheckedAt.map { now.timeIntervalSince($0) > Double(ProtocolConstants.relayListRefreshSeconds) } ?? true
        }.map(\.id))
        let plan = RelayPlan.authorsByRelay(state.contacts.compactMap { contact in
            contact.publicKey.map { ContactRelays(publicKey: $0, relays: contact.queryRelays(defaults: state.relays)) }
        })
        var targets: [RelayUrl: [Filter]] = [:]
        for (relay, authors) in plan {
            var filters = [
                Filter()
                    .kinds(kinds: [Kind(kind: ProtocolConstants.locationKind)])
                    .authors(authors: authors)
                    .identifier(identifier: ProtocolConstants.identifier),
            ]
            let dueAuthors = authors.filter { due.contains($0.toHex()) }
            if !dueAuthors.isEmpty {
                filters.append(Filter().kinds(kinds: [Kind(kind: ProtocolConstants.relayListKind)]).authors(authors: dueAuthors))
            }
            targets[relay] = filters
        }
        let events = await relays.fetch(targets: targets)
        let nowSecs = UInt64(now.timeIntervalSince1970)
        var decoded = 0
        for index in state.contacts.indices {
            guard let publicKey = state.contacts[index].publicKey else { continue }
            let own = events.filter { $0.author() == publicKey }
            if due.contains(state.contacts[index].id) {
                let lists = own.filter { $0.kind().asU16() == ProtocolConstants.relayListKind }
                if let newest = lists.max(by: { $0.createdAt().asSecs() < $1.createdAt().asSecs() }) {
                    let urls = RelayList.writeRelays(of: newest)
                    if !urls.isEmpty { state.contacts[index].relays = urls }
                }
                state.contacts[index].relayListCheckedAt = now
            }
            let locations = own.filter { $0.kind().asU16() == ProtocolConstants.locationKind }
            guard let newest = LocationEventReader.newest(locations, now: nowSecs, cachedCreatedAt: state.contacts[index].lastCreatedAt) else { continue }
            do {
                switch try LocationEventReader.read(event: newest, contact: publicKey, keys: keys, now: nowSecs) {
                case .shared(let payload):
                    state.contacts[index].lastPayload = payload
                    state.contacts[index].lastSeenAt = now
                    state.contacts[index].sharing = .sharing
                    decoded += 1
                case .notSharing:
                    state.contacts[index].sharing = .notSharing
                }
                state.contacts[index].lastCreatedAt = newest.createdAt().asSecs()
                state.contacts[index].lastEventId = newest.id().toHex()
            } catch {
                log("Ignored an event from \(state.contacts[index].name): \(error)")
            }
        }
        save()
        log("Refreshed: \(events.count) events from \(targets.count) relays, \(decoded) new locations")
    }

    // MARK: Helpers

    private func npubPrefix(of publicKey: PublicKey) -> Substring {
        ((try? publicKey.toBech32()) ?? publicKey.toHex()).prefix(12)
    }

    private func save() {
        store.save(state)
    }

    private func log(_ text: String) {
        logger.info("\(text, privacy: .public)")
        #if DEBUG
        FileHandle.standardError.write(Data((text + "\n").utf8))
        #endif
        log.append(LogLine(time: Date(), text: text))
        if log.count > 100 { log.removeFirst(log.count - 100) }
    }
}

@MainActor
private final class BackgroundTask {
    private var id = UIBackgroundTaskIdentifier.invalid

    init(name: String) {
        id = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            self?.end()
        }
    }

    func end() {
        guard id != .invalid else { return }
        UIApplication.shared.endBackgroundTask(id)
        id = .invalid
    }
}
