import Factory
import Foundation
import SimplicityAuth
import SimplicityServices

enum AppStage: Equatable {
    case loading
    case signedOut
    case confirming(email: String)
    case resettingPassword
    case creatingAccount
    case onboardingProfile
    case onboardingOrganisation
    case signedIn
}

@Observable
@MainActor
final class AppRouter {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    private(set) var stage: AppStage = .loading

    // MARK: Functions

    /// Decides the opening screen. Called once, after the API has been configured.
    func start() async {
        if ProcessInfo.processInfo.arguments.contains("--uitest-signed-out") {
            // The simulator keeps its Keychain between runs, so a UI test that assumed a signed-out
            // app would pass or fail depending on what the last run left behind.
            await signOut()
            return
        }

        guard await auth.isSignedIn() else {
            stage = .signedOut
            return
        }
        await resumeSession()
    }

    func show(_ stage: AppStage) {
        self.stage = stage
    }

    func handle(_ outcome: SignInOutcome) {
        switch outcome {
        case .signedIn: stage = .signedIn
        case .needsOnboarding: stage = .onboardingProfile
        case .needsConfirmation: stage = .confirming(email: .empty)
        }
    }

    /// After the profile is saved, the same two gates decide whether an organisation is still
    /// needed — rather than assuming, which would strand anyone who already belongs to one
    /// through an invitation.
    func advanceAfterOnboardingStep() async {
        guard let user = await session.current else {
            stage = .onboardingOrganisation
            return
        }
        stage = user.needsOnboarding ? .onboardingOrganisation : .signedIn
    }

    func signOut() async {
        await auth.signOut()
        await session.clear()
        stage = .signedOut
    }

    // MARK: Private

    private func resumeSession() async {
        do {
            let user = try await session.refresh()
            stage = user.needsOnboarding ? .onboardingProfile : .signedIn
        } catch {
            // A token Cognito still honours but our API rejects means the account is gone or
            // deactivated. Signing out is more honest than an empty app.
            await signOut()
        }
    }
}

private extension String {
    static var empty: String { "" }
}
