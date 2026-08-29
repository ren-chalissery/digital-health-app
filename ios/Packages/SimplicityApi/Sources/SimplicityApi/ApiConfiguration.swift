import Foundation

/// Points the generated client at an environment and teaches it to authenticate.
public enum ApiConfiguration {

    // MARK: Properties

    private static let stored = AdapterBox()

    // MARK: Functions

    /// Called once at launch, and again only if the adapter changes.
    public static func apply(_ adapter: ApiAdapter) async {
        stored.value = adapter
        SimplicityApiAPIConfiguration.shared.basePath = adapter.baseURL.absoluteString
        SimplicityApiAPIConfiguration.shared.interceptor = BearerInterceptor()
    }

    /// Resolved per request rather than cached, because Amplify refreshes the access token when it
    /// is close to expiry and a cached one would go stale mid-session.
    ///
    /// An empty dictionary rather than a throw when signed out: the public endpoints — invitation
    /// preview — are reached the same way, and a missing token is not an error there.
    public static func authorizationHeaders() async -> [String: String] {
        guard let token = await stored.value?.accessToken() else { return [:] }
        return ["Authorization": "Bearer \(token)"]
    }
}

/// The adapter is written once at launch and read from every request, on any thread.
private final class AdapterBox: @unchecked Sendable {

    private let lock = NSLock()
    private var adapter: ApiAdapter?

    var value: ApiAdapter? {
        get { lock.withLock { adapter } }
        set { lock.withLock { adapter = newValue } }
    }
}
