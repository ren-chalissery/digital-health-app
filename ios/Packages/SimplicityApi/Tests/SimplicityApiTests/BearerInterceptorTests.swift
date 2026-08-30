import Foundation
import Testing

@testable import SimplicityApi

/// What the app does when the server says the token is no longer good.
///
/// The interceptor used to never retry, reasoning that a 401 surviving a per-request token meant
/// the session was over. That was true while nothing revoked tokens. Now that removing somebody
/// from an organisation voids the tokens issued before it, the first 401 is precisely the case a
/// refresh fixes — and without a retry, somebody who belongs to a second organisation would sit
/// there failing until their token expired on its own.
@Suite("BearerInterceptor", .serialized)
struct BearerInterceptorTests {

    /// Counts refreshes without touching the process-wide configuration, which another suite is
    /// free to overwrite at any moment.
    private final class RefreshRecorder: @unchecked Sendable {

        private let lock = NSLock()
        private var count = 0

        var refreshCount: Int { lock.withLock { count } }

        func refresh() async -> String? {
            lock.withLock { count += 1 }
            return "fresh"
        }
    }

    @Test("retries once after a 401, having asked for a fresh token")
    func retriesOnUnauthorized() async {
        let recorder = RefreshRecorder()

        let decision = await decision(for: 401, using: BearerInterceptor(refresh: recorder.refresh))

        #expect(decision.isRetry)
        #expect(recorder.refreshCount == 1)
    }

    @Test("does not retry a second time, so a revoked session ends rather than loops")
    func retriesOnlyOnce() async {
        let interceptor = BearerInterceptor(refresh: RefreshRecorder().refresh)

        let first = await decision(for: 401, using: interceptor)
        let second = await decision(for: 401, using: interceptor)

        #expect(first.isRetry)
        #expect(!second.isRetry)
    }

    @Test("does not retry a 403, which a new token would not change")
    func ignoresForbidden() async {
        let interceptor = BearerInterceptor(refresh: RefreshRecorder().refresh)

        #expect(!(await decision(for: 403, using: interceptor)).isRetry)
    }

    // MARK: Helpers

    private func decision(
        for statusCode: Int,
        using interceptor: BearerInterceptor
    ) async -> OpenAPIInterceptorRetry {
        // swiftlint:disable:next force_unwrapping
        let url = URL(string: "https://api.example.com/api/v1/me")!
        let response = HTTPURLResponse(
            url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil
        )

        return await withCheckedContinuation { continuation in
            interceptor.retry(
                urlRequest: URLRequest(url: url),
                urlSession: URLSession.shared,
                requestBuilder: RequestBuilder<Void>(
                    method: "GET",
                    URLString: url.absoluteString,
                    parameters: nil,
                    headers: [:],
                    requiresAuthentication: true
                ),
                data: nil,
                response: response,
                error: URLError(.userAuthenticationRequired),
                completion: { continuation.resume(returning: $0) }
            )
        }
    }
}

extension OpenAPIInterceptorRetry {

    var isRetry: Bool {
        if case .retry = self { return true }
        return false
    }
}
