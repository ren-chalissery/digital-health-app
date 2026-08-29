import SimplicityDesign
import SwiftUI

public struct ForgotPasswordView: View {

    // MARK: Properties

    @State private var model = ForgotPasswordViewModel()
    private let onDone: () -> Void

    // MARK: Init

    public init(onDone: @escaping () -> Void) {
        self.onDone = onDone
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(spacing: Spacing.x4) {
            Text("reset_title", bundle: .module)
                .font(.brandTitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            FormField(
                label: String(localized: "reset_email", bundle: .module),
                text: $model.email,
                kind: .email
            )
            // Locked once the code is on its way: changing it here would send the new code to one
            // address and check it against another.
            .disabled(model.stage != .requestingCode)

            if model.stage == .enteringNewPassword {
                FormField(
                    label: String(localized: "reset_code", bundle: .module),
                    text: $model.code,
                    kind: .oneTimeCode
                )

                FormField(
                    label: String(localized: "reset_new_password", bundle: .module),
                    text: $model.newPassword,
                    kind: .newPassword
                )
            }

            ErrorBanner(message: model.errorMessage)

            if model.stage == .done {
                Text("reset_done", bundle: .module)
                    .font(.brandBody)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                PrimaryButton(title: submitTitle, isLoading: model.isBusy) {
                    Task { await model.submit() }
                }
                .accessibilityIdentifier("reset-submit")
            }

            Spacer()
        }
        .padding(Spacing.x5)
        .onChange(of: model.stage) { _, stage in
            if stage == .done { onDone() }
        }
    }

    private var submitTitle: String {
        model.stage == .requestingCode
            ? String(localized: "reset_send_code", bundle: .module)
            : String(localized: "reset_submit", bundle: .module)
    }
}
