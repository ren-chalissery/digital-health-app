import Testing

@testable import SimplicityTesting

@Suite("InMemorySecureStore")
struct InMemorySecureStoreTests {

    @Test("round-trips and removes without touching the Keychain")
    func roundTripsAndRemoves() throws {
        let store = InMemorySecureStore()
        #expect(try store.string(for: "k") == nil)
        try store.set("v", for: "k")
        #expect(try store.string(for: "k") == "v")
        try store.remove("k")
        #expect(try store.string(for: "k") == nil)
    }
}
