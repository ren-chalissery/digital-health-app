import Foundation

/// Attaches the access token when the request is made rather than when the client was configured.
///
/// That distinction matters: `customHeaders` is captured once, but a fifteen-minute access token
/// will be refreshed several times in a session, so a header set at launch would start failing
/// partway through.
final class BearerInterceptor: OpenAPIInterceptor {

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

    /// Never retry here. A 401 that a refreshed token would fix is already handled by asking for
    /// the token per request; a 401 that survives that means the session is genuinely over, and
    /// retrying would only delay signing the person out.
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
        completion(.dontRetry)
    }

    // swiftlint:enable function_parameter_count
}
