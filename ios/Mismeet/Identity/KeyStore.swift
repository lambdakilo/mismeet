import Foundation
import NostrSDK
import Security

/// The device key pair, spec section 3, kept in the Keychain so that it survives reinstalls of
/// the same bundle and is readable after a reboot once the phone has been unlocked.
enum KeyStore {
    struct Failure: Error {
        let status: OSStatus
    }

    private static let service = "app.mismeet.ios"
    private static let account = "secret-key"

    static func loadOrCreate() throws -> Keys {
        if let hex = try readSecretKeyHex() {
            return try Keys.parse(secretKey: hex)
        }
        let keys = Keys.generate()
        try write(keys.secretKey().toHex())
        return keys
    }

    private static var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private static func readSecretKeyHex() throws -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data, let hex = String(data: data, encoding: .utf8) else {
            throw Failure(status: status)
        }
        return hex
    }

    private static func write(_ hex: String) throws {
        var query = baseQuery
        query[kSecValueData as String] = Data(hex.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw Failure(status: status) }
    }
}
