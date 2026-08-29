import SimplicityDesign
import SwiftUI

public struct ConfirmEmailView: View {

    // MARK: Properties

    @State private var model: ConfirmEmailViewModel
    private let onConfirmed: () -> Void

    // MARK: Init

    public init(email: String, onConfirmed: @escaping () -> Void) {
        self._model = State(initialValue: ConfirmEmailViewModel(email: email))
        self.onConfirmed = onConfirmed
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(spacing: Spacing.x4) {
            Text("confirm_title", bundle: .module)
                .font(.brandTitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(String(localized: "confirm_body", bundle: .module).replacingOccurrences(
                of: "%@",
                with: model.email
            ))
            .font(.brandBody)
            .foregroundStyle(Color.brandTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)

            FormField(
                label: String(localized: "confirm_code", bundle: .module),
                text: $model.code,
                kind: .oneTimeCode
            )

            ErrorBanner(message: model.errorMessage)

            if model.didResend {
                Text("confirm_resent", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            PrimaryButton(
                title: String(localized: "confirm_submit", bundle: .module),
                isLoading: model.isBusy
            ) {
                Task { await model.submit() }
            }
            .accessibilityIdentifier("confirm-submit")

            Button {
                Task { await model.resend() }
            } label: {
                Text("confirm_resend", bundle: .module).font(.brandCaption)
            }

            Spacer()
        }
        .padding(Spacing.x5)
        .onChange(of: model.didConfirm) { _, confirmed in
            if confirmed { onConfirmed() }
        }
    }
}
