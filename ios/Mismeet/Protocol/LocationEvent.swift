import Foundation
import NostrSDK

enum LocationEventError: Error, Equatable {
    case tooManyRecipients(Int)
}

enum LocationEventRejection: Error, Equatable {
    case wrongAuthor
    case wrongKind
    case wrongIdentifier
    case invalidSignature
    case missingExpiration
    case malformedExpiration
    case expired
    case malformedContent
    case unsupportedContentVersion
}

enum LocationReadResult: Equatable {
    case shared(LocationPayload)
    case notSharing
}

/// Builds location events, spec sections 5 and 7.2.
enum LocationEventBuilder {
    static func bucketSize(recipients count: Int) throws -> Int {
        guard let bucket = ProtocolConstants.entryBuckets.first(where: { $0 >= count }) else {
            throw LocationEventError.tooManyRecipients(count)
        }
        return bucket
    }

    static func createdAt(now: UInt64, previous: UInt64?) -> UInt64 {
        guard let previous else { return now }
        return max(now, previous + 1)
    }

    static func entries(plaintext: String, recipients: [PublicKey], keys: Keys) throws -> [String] {
        let bucket = try bucketSize(recipients: recipients.count)
        let secretKey = keys.secretKey()
        var entries = try recipients.map { recipient in
            try nip44Encrypt(secretKey: secretKey, publicKey: recipient, content: plaintext, version: .v2)
        }
        let fillerLength = plaintext.utf8.count
        for _ in recipients.count..<bucket {
            let throwaway = Keys.generate()
            entries.append(try nip44Encrypt(
                secretKey: secretKey,
                publicKey: throwaway.publicKey(),
                content: randomFiller(length: fillerLength),
                version: .v2
            ))
        }
        var generator = SystemRandomNumberGenerator()
        entries.shuffle(using: &generator)
        return entries
    }

    static func content(entries: [String]) -> String {
        let list = entries.map { "\"\($0)\"" }.joined(separator: ",")
        return "{\"v\":\(ProtocolConstants.contentVersion),\"c\":[\(list)]}"
    }

    static func build(
        payload: LocationPayload,
        recipients: [PublicKey],
        keys: Keys,
        now: UInt64,
        previousCreatedAt: UInt64?
    ) throws -> Event {
        try sign(
            entries: try entries(plaintext: payload.encode(), recipients: recipients, keys: keys),
            keys: keys,
            createdAt: createdAt(now: now, previous: previousCreatedAt)
        )
    }

    /// The all-dummy event of spec section 8.3, published when a contact is removed and no recent fix exists.
    static func buildRevocation(keys: Keys, now: UInt64, previousCreatedAt: UInt64?) throws -> Event {
        try sign(
            entries: try entries(
                plaintext: randomFiller(length: ProtocolConstants.revocationFillerLength),
                recipients: [],
                keys: keys
            ),
            keys: keys,
            createdAt: createdAt(now: now, previous: previousCreatedAt)
        )
    }

    static func randomFiller(length: Int) -> String {
        let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        var generator = SystemRandomNumberGenerator()
        return String((0..<length).map { _ in alphabet.randomElement(using: &generator)! })
    }

    private static func sign(entries: [String], keys: Keys, createdAt: UInt64) throws -> Event {
        let tags = [
            Tag.identifier(identifier: ProtocolConstants.identifier),
            Tag.expiration(timestamp: Timestamp.fromSecs(secs: createdAt + ProtocolConstants.expirationSeconds)),
        ]
        let unsigned = EventBuilder(kind: Kind(kind: ProtocolConstants.locationKind), content: content(entries: entries))
            .tags(tags: tags)
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: createdAt))
            .finalizeUnsigned(publicKey: keys.publicKey())
        return try keys.signEvent(unsignedEvent: unsigned)
    }
}

/// Reads location events, spec sections 5.5, 7.3 and 8.4.
enum LocationEventReader {
    static func expiration(of event: Event) throws -> UInt64 {
        guard let tag = event.tags().first(where: { $0.kind() == "expiration" }) else {
            throw LocationEventRejection.missingExpiration
        }
        guard let text = tag.content(), let expiration = UInt64(text) else {
            throw LocationEventRejection.malformedExpiration
        }
        return expiration
    }

    static func entries(of event: Event, contact: PublicKey, now: UInt64) throws -> [String] {
        guard event.author() == contact else { throw LocationEventRejection.wrongAuthor }
        guard event.kind().asU16() == ProtocolConstants.locationKind else { throw LocationEventRejection.wrongKind }
        let identifier = event.tags().first(where: { $0.kind() == "d" })?.content()
        guard identifier == ProtocolConstants.identifier else { throw LocationEventRejection.wrongIdentifier }
        guard event.verify() else { throw LocationEventRejection.invalidSignature }
        guard try expiration(of: event) > now else { throw LocationEventRejection.expired }
        return try parseContent(event.content())
    }

    static func parseContent(_ content: String) throws -> [String] {
        guard let data = content.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let fields = object as? [String: Any] else {
            throw LocationEventRejection.malformedContent
        }
        guard let version = fields["v"] as? Int, version == ProtocolConstants.contentVersion else {
            throw LocationEventRejection.unsupportedContentVersion
        }
        guard let entries = fields["c"] as? [String] else { throw LocationEventRejection.malformedContent }
        return entries
    }

    static func read(event: Event, contact: PublicKey, keys: Keys, now: UInt64) throws -> LocationReadResult {
        let secretKey = keys.secretKey()
        for entry in try entries(of: event, contact: contact, now: now) {
            guard let plaintext = try? nip44Decrypt(secretKey: secretKey, publicKey: contact, payload: entry) else {
                continue
            }
            if let payload = try? LocationPayload.decode(plaintext) {
                return .shared(payload)
            }
        }
        return .notSharing
    }

    /// The event to read among those fetched for one contact: unexpired, newer than the cache,
    /// greatest `created_at`, lowest id on a tie.
    static func newest(_ events: [Event], now: UInt64, cachedCreatedAt: UInt64?) -> Event? {
        events
            .filter { event in
                guard let expiration = try? expiration(of: event), expiration > now else { return false }
                guard let cachedCreatedAt else { return true }
                return event.createdAt().asSecs() > cachedCreatedAt
            }
            .min { lhs, rhs in
                let left = lhs.createdAt().asSecs()
                let right = rhs.createdAt().asSecs()
                if left != right { return left > right }
                return lhs.id().toHex() < rhs.id().toHex()
            }
    }
}
