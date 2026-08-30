import Foundation

/// What this package needs but cannot know for itself: where the API is, and who is calling.
///
/// The app supplies it. Keeping it a protocol is what lets the package be tested without an app,
/// and what keeps Cognito out of it entirely — nothing here knows how a token is obtained, only
/// that asking for one may take a moment and may come back empty.
public protocol ApiAdapter: Sendable {
    var baseURL: URL { get }
    func accessToken() async -> String?

    /// A token obtained by forcing a refresh, rather than whatever is cached.
    ///
    /// Needed because the server can now void a token before it expires — being removed from an
    /// organisation does exactly that. The cached token would keep failing until it aged out, so a
    /// rejected request has to be able to insist on a new one.
    func refreshedAccessToken() async -> String?
}
