import Foundation

/// Attaches the access token when the request is made rather than when the client was configured.
///
/// That distinction matters: `customHeaders` is captured once, but a fifteen-minute access token
/// will be refreshed several times in a session, so a header set at launch would start failing
/// partway through.
final class BearerInterceptor: OpenAPIInterceptor {

    /// Injectable so a test can observe refreshes without reaching through process-wide state,
    /// which two suites cannot share safely.
    private let refresh: @Sendable () async -> String?

    init(refresh: @Sendable @escaping () async -> String? = {
        await ApiConfiguration.refreshAccessToken()
    }) {
        self.refresh = refresh
    }

    func intercept<T>(
        urlRequest: URLRequest,
        urlSession: URLSessionProtocol,
        requestBuilder: RequestBuilder<T>,
        completion: @Sendable @escaping (Result<URLRequest, Error>) -> Void
    ) {
        Task {
            var request = urlRequest
            for (name, value) in await ApiConfiguration.authorizationHeaders() {
                request.setValue(value, forHTTPHeaderField: name)
            }
            completion(.success(request))
        }
    }

    // swiftlint:disable function_parameter_count

    /// Retry a 401 exactly once, having forced a new token.
    ///
    /// This used to never retry, on the reasoning that resolving the token per request already
    /// handled anything a refresh could fix. That held while only expiry could invalidate a token.
    /// The server now voids tokens issued before somebody's access changed, so the first 401 is
    /// precisely the case a refresh fixes — and for somebody who belongs to a second organisation,
    /// not retrying means failing every request until the old token ages out.
    ///
    /// Once, not in a loop: a second 401 means the session really is over.
    ///
    /// The parameter count is the generated protocol's, not a choice.
    func retry<T>(
        urlRequest: URLRequest,
        urlSession: URLSessionProtocol,
        requestBuilder: RequestBuilder<T>,
        data: Data?,
        response: URLResponse?,
        error: Error,
        completion: @Sendable @escaping (OpenAPIInterceptorRetry) -> Void
    ) {
        guard (response as? HTTPURLResponse)?.statusCode == 401, refreshes.mayRefresh() else {
            // A 403 is authorisation, not authentication; a new token says nothing new about it.
            completion(.dontRetry)
            return
        }

        Task {
            guard await refresh() != nil else {
                completion(.dontRetry)
                return
            }
            completion(.retry)
        }
    }

    // swiftlint:enable function_parameter_count

    private let refreshes = RefreshThrottle()
}

/// Bounds how often a 401 may provoke a token refresh.
///
/// A latch that never reset would stop the app recovering from any later revocation, and no limit
/// at all would let a persistently rejected token spin: 401, refresh, 401, refresh. A short window
/// gives one refresh per burst of rejections, lets the second 401 surface as a real error, and
/// still allows a genuine refresh half an hour later.
private final class RefreshThrottle: @unchecked Sendable {

    private let interval: TimeInterval = 10
    private let lock = NSLock()
    private var last: Date?

    func mayRefresh() -> Bool {
        lock.withLock {
            let now = Date()
            if let last, now.timeIntervalSince(last) < interval {
                return false
            }
            last = now
            return true
        }
    }
}
