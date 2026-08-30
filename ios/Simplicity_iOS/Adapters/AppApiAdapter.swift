import Factory
import Foundation
import SimplicityApi
import SimplicityAuth

/// The app's answer to what `SimplicityApi` cannot know for itself.
///
/// This is the only place the API package and the auth package meet. Neither depends on the other;
/// the app composes them, which is what lets each be tested without the other.
struct AppApiAdapter: ApiAdapter {

    let baseURL = AppConfiguration.apiBaseURL

    func accessToken() async -> String? {
        await Container.shared.authService().accessToken()
    }

    func refreshedAccessToken() async -> String? {
        await Container.shared.authService().refreshedAccessToken()
    }
}
