import Foundation

/// What this package needs but cannot know for itself: where the API is, and who is calling.
///
/// The app supplies it. Keeping it a protocol is what lets the package be tested without an app,
/// and what keeps Cognito out of it entirely — nothing here knows how a token is obtained, only
/// that asking for one may take a moment and may come back empty.
public protocol ApiAdapter: Sendable {
    var baseURL: URL { get }
    func accessToken() async -> String?
}
