import Foundation
import Testing

@testable import SimplicityAuth

/// A guard, not a copy test.
///
/// Resources were originally `.xcstrings`, which Xcode compiles into a `.loctable` but
/// `swift package` only copies. Every lookup silently returned its own key, and nothing failed —
/// the screens would have shown `sign_in_failed` to a clinician. One assertion that a known key
/// resolves to something other than itself catches that the moment it recurs.
@Suite("Localisation")
struct LocalisationTests {

    @Test("a known key resolves to real copy rather than to itself")
    func stringsBundleIsReadable() {
        let resolved = String(localized: "sign_in_submit", bundle: .module)

        #expect(resolved != "sign_in_submit")
        #expect(resolved == "Sign in")
    }

    @Test("the password policy explains itself rather than returning a key")
    func policyMessagesAreCopy() {
        let message = PasswordPolicy.validate("short")

        #expect(message?.contains("12") == true)
    }
}
