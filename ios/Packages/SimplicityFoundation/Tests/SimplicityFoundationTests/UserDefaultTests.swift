import Foundation
import Testing

@testable import SimplicityFoundation

@Suite("UserDefault")
struct UserDefaultTests {

    /// A suite per test, so one test's write is invisible to the next.
    private func store() -> UserDefaults {
        guard let suite = UserDefaults(suiteName: "test-\(UUID().uuidString)") else {
            fatalError("Could not create an isolated defaults suite")
        }
        return suite
    }

    @Test("returns the default when nothing has been written")
    func returnsDefaultWhenUnset() {
        let wrapper = UserDefault(.lastSignedInEmail, default: String.empty, store: store())
        #expect(wrapper.wrappedValue == String.empty)
    }

    @Test("returns what was written")
    func returnsWrittenValue() {
        var wrapper = UserDefault(.lastSignedInEmail, default: String.empty, store: store())
        wrapper.wrappedValue = "clinician@example.com"
        #expect(wrapper.wrappedValue == "clinician@example.com")
    }
}
