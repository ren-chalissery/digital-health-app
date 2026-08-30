import SimplicityApi
import SimplicityAuth
import SwiftUI

@main
struct SimplicityApp: App {

    // MARK: Properties

    @State private var router = AppRouter()

    // MARK: Init

    init() {
        do {
            try AmplifyAuthService.configure()
        } catch {
            // Nothing in the app works without Cognito, and a misconfigured plugin is a build
            // problem rather than something a person can retry past.
            fatalError("Amplify failed to configure: \(error)")
        }
    }

    // MARK: SwiftUI

    var body: some Scene {
        WindowGroup {
            RootView(router: router)
                .privacyScreen()
                .task {
                    await ApiConfiguration.apply(AppApiAdapter())
                    await router.start()
                }
        }
    }
}
