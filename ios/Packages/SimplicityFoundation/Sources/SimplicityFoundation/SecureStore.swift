import Foundation
import Security

public enum SecureStoreError: Error {
    case unexpectedStatus(OSStatus)
    case unreadableData
}

/// Anything that needs the Keychain depends on this, so tests can substitute memory.
public protocol SecureStore: Sendable {
    func string(for key: String) throws -> String?
    func set(_ value: String, for key: String) throws
    func remove(_ key: String) throws
}

public final class KeychainStore: SecureStore {

    // MARK: Properties

    private let service: String

    // MARK: Init

    public init(service: String) {
        self.service = service
    }

    // MARK: Functions

    public func string(for key: String) throws -> String? {
        var query = baseQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw SecureStoreError.unexpectedStatus(status) }
        guard let data = item as? Data, let value = String(data: data, encoding: .utf8) else {
            throw SecureStoreError.unreadableData
        }
        return value
    }

    public func set(_ value: String, for key: String) throws {
        // Delete first: SecItemAdd fails rather than replacing, and an update path would need the
        // same query built twice for no gain.
        try remove(key)

        var query = baseQuery(for: key)
        query[kSecValueData as String] = Data(value.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw SecureStoreError.unexpectedStatus(status) }
    }

    public func remove(_ key: String) throws {
        let status = SecItemDelete(baseQuery(for: key) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SecureStoreError.unexpectedStatus(status)
        }
    }

    // MARK: Private

    private func baseQuery(for key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}
