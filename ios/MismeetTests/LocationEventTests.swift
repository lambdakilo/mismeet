import XCTest
import NostrSDK
@testable import Mismeet

final class LocationEventTests: XCTestCase {
    private let now: UInt64 = 1_759_840_123
    private let publisher = Keys.generate()
    private let recipients = [Keys.generate(), Keys.generate(), Keys.generate()]
    private let outsider = Keys.generate()
    private let payload = LocationPayload(fixTime: 1_759_840_101, latitude: 60.169856, longitude: 24.938379, accuracy: 12)

    private func recipientKeys() -> [PublicKey] { recipients.map { $0.publicKey() } }

    private func craft(kind: UInt16 = ProtocolConstants.locationKind, tags: [Tag], content: String, createdAt: UInt64? = nil) throws -> Event {
        let unsigned = EventBuilder(kind: Kind(kind: kind), content: content)
            .tags(tags: tags)
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: createdAt ?? now))
            .finalizeUnsigned(publicKey: publisher.publicKey())
        return try publisher.signEvent(unsignedEvent: unsigned)
    }

    private func validTags(expiration: UInt64? = nil) -> [Tag] {
        [
            Tag.identifier(identifier: ProtocolConstants.identifier),
            Tag.expiration(timestamp: Timestamp.fromSecs(secs: expiration ?? now + ProtocolConstants.expirationSeconds)),
        ]
    }

    func testBucketSizes() throws {
        let expected = [0: 4, 1: 4, 4: 4, 5: 8, 8: 8, 9: 16, 16: 16, 17: 32, 32: 32, 33: 64, 64: 64]
        for (count, bucket) in expected {
            XCTAssertEqual(try LocationEventBuilder.bucketSize(recipients: count), bucket, "\(count) recipients")
        }
        XCTAssertThrowsError(try LocationEventBuilder.bucketSize(recipients: 65)) {
            XCTAssertEqual($0 as? LocationEventError, .tooManyRecipients(65))
        }
    }

    func testCreatedAtIsStrictlyGreaterThanThePreviousOne() {
        XCTAssertEqual(LocationEventBuilder.createdAt(now: now, previous: nil), now)
        XCTAssertEqual(LocationEventBuilder.createdAt(now: now, previous: now - 5), now)
        XCTAssertEqual(LocationEventBuilder.createdAt(now: now, previous: now), now + 1)
        XCTAssertEqual(LocationEventBuilder.createdAt(now: now, previous: now + 100), now + 101)
    }

    func testBuildsTheEventOfSectionFive() throws {
        let event = try LocationEventBuilder.build(
            payload: payload, recipients: recipientKeys(), keys: publisher, now: now, previousCreatedAt: nil
        )
        XCTAssertTrue(event.verify())
        XCTAssertEqual(event.kind().asU16(), 31122)
        XCTAssertEqual(event.createdAt().asSecs(), now)
        let tags = event.tags().map { $0.toVec() }
        XCTAssertEqual(tags, [["d", "location"], ["expiration", String(now + 86_400)]])

        let entries = try LocationEventReader.parseContent(event.content())
        XCTAssertEqual(entries.count, 4)
        XCTAssertEqual(Set(entries.map(\.count)).count, 1, "dummies have the length of real entries")

        for recipient in recipients {
            let decrypted = entries.compactMap {
                try? nip44Decrypt(secretKey: recipient.secretKey(), publicKey: publisher.publicKey(), payload: $0)
            }
            XCTAssertEqual(decrypted, [payload.encode()])
        }
        let leaked = entries.compactMap {
            try? nip44Decrypt(secretKey: outsider.secretKey(), publicKey: publisher.publicKey(), payload: $0)
        }
        XCTAssertEqual(leaked, [])
    }

    func testRevocationEventDecryptsForNobody() throws {
        let event = try LocationEventBuilder.buildRevocation(keys: publisher, now: now, previousCreatedAt: now)
        XCTAssertEqual(event.createdAt().asSecs(), now + 1)
        let entries = try LocationEventReader.parseContent(event.content())
        XCTAssertEqual(entries.count, 4)
        for recipient in recipients {
            XCTAssertEqual(
                try LocationEventReader.read(event: event, contact: publisher.publicKey(), keys: recipient, now: now),
                .notSharing
            )
        }
    }

    func testReaderReturnsThePayloadToRecipientsOnly() throws {
        let event = try LocationEventBuilder.build(
            payload: payload, recipients: recipientKeys(), keys: publisher, now: now, previousCreatedAt: nil
        )
        for recipient in recipients {
            XCTAssertEqual(
                try LocationEventReader.read(event: event, contact: publisher.publicKey(), keys: recipient, now: now),
                .shared(payload)
            )
        }
        XCTAssertEqual(
            try LocationEventReader.read(event: event, contact: publisher.publicKey(), keys: outsider, now: now),
            .notSharing
        )
    }

    func testReaderRejectsBadEvents() throws {
        let contact = publisher.publicKey()
        let content = LocationEventBuilder.content(entries: [])
        func rejection(_ event: Event, contact: PublicKey? = nil, now: UInt64? = nil) -> LocationEventRejection? {
            do {
                _ = try LocationEventReader.entries(of: event, contact: contact ?? self.publisher.publicKey(), now: now ?? self.now)
                return nil
            } catch {
                return error as? LocationEventRejection
            }
        }
        let good = try craft(tags: validTags(), content: content)
        XCTAssertNil(rejection(good))
        XCTAssertEqual(rejection(good, contact: outsider.publicKey()), .wrongAuthor)
        XCTAssertEqual(rejection(good, now: now + 86_400), .expired)
        XCTAssertEqual(rejection(try craft(kind: 1, tags: validTags(), content: content)), .wrongKind)
        XCTAssertEqual(
            rejection(try craft(tags: [Tag.identifier(identifier: "other"), validTags()[1]], content: content)),
            .wrongIdentifier
        )
        XCTAssertEqual(rejection(try craft(tags: [validTags()[0]], content: content)), .missingExpiration)
        XCTAssertEqual(
            rejection(try craft(tags: [validTags()[0], Tag.parse(data: ["expiration", "soon"])], content: content)),
            .malformedExpiration
        )
        XCTAssertEqual(rejection(try craft(tags: validTags(), content: "{\"v\":2,\"c\":[]}")), .unsupportedContentVersion)
        XCTAssertEqual(rejection(try craft(tags: validTags(), content: "[]")), .malformedContent)
        XCTAssertEqual(rejection(try craft(tags: validTags(), content: "{\"v\":1,\"c\":[1]}")), .malformedContent)
        XCTAssertEqual(rejection(try craft(tags: validTags(), content: "not json")), .malformedContent)
    }

    func testNewestPicksGreatestCreatedAtThenLowestId() throws {
        let content = LocationEventBuilder.content(entries: [])
        let older = try craft(tags: validTags(), content: content, createdAt: now - 10)
        let newer = try craft(tags: validTags(), content: content, createdAt: now)
        let tie = try craft(tags: validTags(), content: LocationEventBuilder.content(entries: ["x"]), createdAt: now)
        let expired = try craft(tags: validTags(expiration: now), content: content, createdAt: now + 5)

        XCTAssertEqual(LocationEventReader.newest([older, newer], now: now, cachedCreatedAt: nil), newer)
        XCTAssertEqual(LocationEventReader.newest([newer, older], now: now, cachedCreatedAt: nil), newer)
        let lowest = [newer, tie].min { $0.id().toHex() < $1.id().toHex() }
        XCTAssertEqual(LocationEventReader.newest([newer, tie], now: now, cachedCreatedAt: nil), lowest)
        XCTAssertEqual(LocationEventReader.newest([older, newer, expired], now: now, cachedCreatedAt: nil), newer)
        XCTAssertEqual(LocationEventReader.newest([older, newer], now: now, cachedCreatedAt: now - 10), newer)
        XCTAssertNil(LocationEventReader.newest([older, newer], now: now, cachedCreatedAt: now))
        XCTAssertNil(LocationEventReader.newest([], now: now, cachedCreatedAt: nil))
    }
}
