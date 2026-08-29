import Foundation
import Testing

@testable import SimplicityFoundation

@Suite("UserDefault")
struct UserDefaultTests {

    private func store() -> UserDefaults {
        UserDefaults(suiteName: "test-\(UUID().uuidString)")!
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
