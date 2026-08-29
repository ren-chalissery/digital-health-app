import SimplicityAuth
import SimplicityDesign
import SwiftUI

struct RootView: View {

    // MARK: Properties

    @Bindable var router: AppRouter

    // MARK: SwiftUI

    var body: some View {
        switch router.stage {
        case .loading:
            ProgressView()

        case .signedOut:
            NavigationStack {
                SignInView { router.handle($0) }
                    .toolbar { signedOutToolbar }
            }

        case .creatingAccount:
            SignUpView(
                onSignedUp: { router.show(.confirming(email: $0)) },
                onHaveAccount: { router.show(.signedOut) }
            )

        case let .confirming(email):
            ConfirmEmailView(email: email) { router.show(.signedOut) }

        case .resettingPassword:
            ForgotPasswordView { router.show(.signedOut) }

        case .onboardingProfile:
            ProfileWizardView { Task { await router.advanceAfterOnboardingStep() } }

        case .onboardingOrganisation:
            OrganisationWizardView { Task { await router.advanceAfterOnboardingStep() } }

        case .signedIn:
            MainTabView { Task { await router.signOut() } }
        }
    }

    @ToolbarContentBuilder
    private var signedOutToolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button("Create an account") { router.show(.creatingAccount) }
                .font(.brandCaption)
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button("Forgotten?") { router.show(.resettingPassword) }
                .font(.brandCaption)
        }
    }
}
