import SimplicityDesign
import SwiftUI

public struct SignInView: View {

    // MARK: Properties

    @State private var model = SignInViewModel()
    private let onOutcome: (SignInOutcome) -> Void

    // MARK: Init

    public init(onOutcome: @escaping (SignInOutcome) -> Void) {
        self.onOutcome = onOutcome
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(spacing: Spacing.x4) {
            Text("sign_in_title", bundle: .module)
                .font(.brandTitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            FormField(
                label: String(localized: "sign_in_email", bundle: .module),
                text: $model.email,
                kind: .email
            )

            FormField(
                label: String(localized: "sign_in_password", bundle: .module),
                text: $model.password,
                kind: .password
            )

            ErrorBanner(message: model.errorMessage)

            PrimaryButton(
                title: String(localized: "sign_in_submit", bundle: .module),
                isLoading: model.isBusy
            ) {
                Task { await model.submit() }
            }
            .accessibilityIdentifier("sign-in-submit")

            Spacer()
        }
        .padding(Spacing.x5)
        .onChange(of: model.outcome) { _, outcome in
            if let outcome { onOutcome(outcome) }
        }
    }
}
