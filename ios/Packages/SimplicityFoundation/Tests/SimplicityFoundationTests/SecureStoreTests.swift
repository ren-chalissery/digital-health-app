import Foundation
import Testing

@testable import SimplicityFoundation

@Suite("KeychainStore")
struct SecureStoreTests {

    private enum Constants {
        static let key = "token"
    }

    private func subject() -> KeychainStore {
        KeychainStore(service: "test-\(UUID().uuidString)")
    }

    @Test("returns nil for a key that was never written")
    func missingKeyIsNil() throws {
        #expect(try subject().string(for: Constants.key) == nil)
    }

    @Test("round-trips a value")
    func roundTrips() throws {
        let store = subject()
        try store.set("abc123", for: Constants.key)
        #expect(try store.string(for: Constants.key) == "abc123")
    }

    @Test("overwrites rather than duplicating")
    func overwrites() throws {
        let store = subject()
        try store.set("first", for: Constants.key)
        try store.set("second", for: Constants.key)
        #expect(try store.string(for: Constants.key) == "second")
    }

    @Test("removes a value")
    func removes() throws {
        let store = subject()
        try store.set("abc123", for: Constants.key)
        try store.remove(Constants.key)
        #expect(try store.string(for: Constants.key) == nil)
    }
}
