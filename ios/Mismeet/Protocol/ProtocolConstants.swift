import Foundation

/// The values of spec/SPEC.md section 10. Change the spec first.
enum ProtocolConstants {
    static let locationKind: UInt16 = 31122
    static let relayListKind: UInt16 = 10002
    static let identifier = "location"
    static let contentVersion = 1
    static let payloadVersion = 1
    static let expirationSeconds: UInt64 = 86_400
    static let entryBuckets = [4, 8, 16, 32, 64]
    static let maxSharingContacts = 64
    static let minRelaysPerList = 2
    static let maxRelaysPerList = 4
    static let maxRelaysPerContact = 8
    static let relayListRefreshSeconds: UInt64 = 86_400
    static let publishThrottleSeconds: TimeInterval = 60
    static let maxFixAgeSeconds: TimeInterval = 3_600
    static let foregroundFetchIntervalSeconds: TimeInterval = 60
    static let connectTimeoutSeconds: TimeInterval = 5
    static let okTimeoutSeconds: TimeInterval = 10
    static let publishTimeoutSeconds: TimeInterval = 25
    static let rateLimitBackoffSeconds: TimeInterval = 300
    static let coordinateDecimals = 6
    static let revocationFillerLength = 100
}
