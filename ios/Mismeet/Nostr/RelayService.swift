import Foundation
import NostrSDK

struct PublishResult: Sendable {
    var succeeded: [String]
    var failed: [String: String]

    var summary: String {
        failed.map { "\($0.key): \($0.value)" }.sorted().joined(separator: "; ")
    }
}

/// One short-lived client per operation: connect, do the work, shut down. Spec section 7.2 and 7.3.
actor RelayService {
    func publish(event: Event, relayUrls: [String]) async -> PublishResult {
        guard let client = await connectedClient(to: relayUrls) else {
            return PublishResult(succeeded: [], failed: ["relays": "none reachable"])
        }
        defer { Task { await client.shutdown() } }
        do {
            let output = try await client.sendEvent(
                event: event,
                target: SendEventTarget.broadcast(),
                ackPolicy: AckPolicy.all(),
                okTimeout: ProtocolConstants.okTimeoutSeconds,
                authenticationTimeout: nil
            )
            return PublishResult(
                succeeded: output.success.map(\.description),
                failed: Dictionary(output.failed.map { ($0.key.description, $0.value) }, uniquingKeysWith: { first, _ in first })
            )
        } catch {
            return PublishResult(succeeded: [], failed: ["send": "\(error)"])
        }
    }

    func fetch(targets: [RelayUrl: [Filter]]) async -> [Event] {
        guard let client = await connectedClient(to: targets.keys.map(\.description)) else { return [] }
        defer { Task { await client.shutdown() } }
        do {
            return try await client.fetchEvents(
                target: ReqTarget.manual(targets: targets),
                timeout: ProtocolConstants.okTimeoutSeconds,
                policy: .exitOnEose,
                maxEvents: nil
            )
        } catch {
            return []
        }
    }

    private func connectedClient(to relayUrls: [String]) async -> Client? {
        let client = ClientBuilder().connectTimeout(timeout: ProtocolConstants.connectTimeoutSeconds).build()
        var added = 0
        for text in relayUrls {
            guard let url = try? RelayUrl.parse(url: text) else { continue }
            if (try? await client.addRelay(url: url)) != nil { added += 1 }
        }
        guard added > 0 else { return nil }
        _ = await client.tryConnect(timeout: ProtocolConstants.connectTimeoutSeconds)
        return client
    }
}
