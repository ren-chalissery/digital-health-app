import Foundation

/// Keys are enumerated so a typo cannot silently read a value nobody ever writes.
public enum PersistedKey: String {
    case lastSignedInEmail
}

@propertyWrapper
public struct UserDefault<Value> {

    // MARK: Properties

    private let key: String
    private let defaultValue: Value
    private let store: UserDefaults

    public var wrappedValue: Value {
        get { store.object(forKey: key) as? Value ?? defaultValue }
        set { store.set(newValue, forKey: key) }
    }

    // MARK: Init

    public init(_ key: PersistedKey, default defaultValue: Value, store: UserDefaults = .standard) {
        self.key = key.rawValue
        self.defaultValue = defaultValue
        self.store = store
    }
}
