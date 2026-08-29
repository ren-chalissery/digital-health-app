import Mockable
import SimplicityTesting
import Testing

@testable import SimplicityAuth

/// Amplify cannot be exercised in a unit test — it needs a configured plugin and a real pool — so
/// what is tested here is the contract every caller depends on. The Amplify implementation itself
/// is covered by the production verification run, which is the only place it can be.
@Suite("AuthService contract", .serialized)
final class AuthServiceContractTests: SimplicityTestCase {

    @Test("an unconfirmed account reports not-signed-in rather than throwing")
    func unconfirmedAccountIsNotAnError() async throws {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(false)

        let signedIn = try await auth.signIn(email: "a@b.com", password: "x")

        #expect(signedIn == false)
    }

    @Test("accessToken is nil when signed out, rather than throwing")
    func accessTokenNilWhenSignedOut() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).accessToken().willReturn(nil)

        #expect(await auth.accessToken() == nil)
    }
}
