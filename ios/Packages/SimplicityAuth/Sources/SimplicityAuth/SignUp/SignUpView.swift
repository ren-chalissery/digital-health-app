import SimplicityDesign
import SwiftUI

public struct SignUpView: View {

    // MARK: Properties

    @State private var model = SignUpViewModel()
    private let onSignedUp: (String) -> Void
    private let onHaveAccount: () -> Void

    // MARK: Init

    public init(onSignedUp: @escaping (String) -> Void, onHaveAccount: @escaping () -> Void) {
        self.onSignedUp = onSignedUp
        self.onHaveAccount = onHaveAccount
    }

    // MARK: SwiftUI

    public var body: some View {
        ScrollView {
            VStack(spacing: Spacing.x4) {
                Text("sign_up_title", bundle: .module)
                    .font(.brandTitle)
                    .frame(maxWidth: .infinity, alignment: .leading)

                FormField(
                    label: String(localized: "sign_up_email", bundle: .module),
                    text: $model.email,
                    kind: .email
                )

                FormField(
                    label: String(localized: "sign_up_password", bundle: .module),
                    text: $model.password,
                    kind: .newPassword
                )

                FormField(
                    label: String(localized: "sign_up_confirm_password", bundle: .module),
                    text: $model.confirmPassword,
                    kind: .newPassword
                )

                ErrorBanner(message: model.errorMessage)

                PrimaryButton(
                    title: String(localized: "sign_up_submit", bundle: .module),
                    isLoading: model.isBusy
                ) {
                    Task { await model.submit() }
                }
                .accessibilityIdentifier("sign-up-submit")

                Button(action: onHaveAccount) {
                    Text("sign_up_have_account", bundle: .module)
                        .font(.brandCaption)
                }
            }
            .padding(Spacing.x5)
        }
        .onChange(of: model.didSignUp) { _, email in
            if let email { onSignedUp(email) }
        }
    }
}
