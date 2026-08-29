import Foundation
import SimplicityFoundation

public final class InMemorySecureStore: SecureStore, @unchecked Sendable {

    // MARK: Properties

    private var values: [String: String] = [:]
    private let lock = NSLock()

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func string(for key: String) throws -> String? {
        lock.withLock { values[key] }
    }

    public func set(_ value: String, for key: String) throws {
        lock.withLock { values[key] = value }
    }

    public func remove(_ key: String) throws {
        _ = lock.withLock { values.removeValue(forKey: key) }
    }
}
