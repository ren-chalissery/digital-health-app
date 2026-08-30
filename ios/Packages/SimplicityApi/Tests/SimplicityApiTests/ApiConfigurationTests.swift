import Foundation
import Testing

@testable import SimplicityApi

/// Serialized because `ApiConfiguration` configures a process-wide client. Run in parallel, one
/// test's adapter replaces another's before it is read.
@Suite("ApiConfiguration", .serialized)
struct ApiConfigurationTests {

    private struct StubAdapter: ApiAdapter {
        // swiftlint:disable:next force_unwrapping
        let baseURL = URL(string: "https://api.example.com")!
        let token: String?

        func accessToken() async -> String? { token }
        func refreshedAccessToken() async -> String? { token }
    }

    @Test("points the generated client at the adapter's base URL")
    func setsBasePath() async {
        await ApiConfiguration.apply(StubAdapter(token: nil))
        #expect(SimplicityApiAPIConfiguration.shared.basePath == "https://api.example.com")
    }

    @Test("attaches a bearer header when there is a token")
    func attachesBearer() async {
        await ApiConfiguration.apply(StubAdapter(token: "abc123"))
        #expect(await ApiConfiguration.authorizationHeaders() == ["Authorization": "Bearer abc123"])
    }

    @Test("sends no authorization header when signed out")
    func omitsBearerWhenSignedOut() async {
        await ApiConfiguration.apply(StubAdapter(token: nil))
        #expect(await ApiConfiguration.authorizationHeaders().isEmpty)
    }

    @Test("installs an interceptor, so the header is resolved per request rather than at launch")
    func installsInterceptor() async {
        await ApiConfiguration.apply(StubAdapter(token: "abc123"))
        #expect(SimplicityApiAPIConfiguration.shared.interceptor is BearerInterceptor)
    }
}
