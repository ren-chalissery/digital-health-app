import SimplicityDesign
import SwiftUI

public struct ProfileWizardView: View {

    // MARK: Properties

    @State private var model = ProfileWizardViewModel()
    private let onComplete: () -> Void

    // MARK: Init

    public init(onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
    }

    // MARK: SwiftUI

    public var body: some View {
        ScrollView {
            VStack(spacing: Spacing.x4) {
                Text("onboarding_profile_title", bundle: .module)
                    .font(.brandTitle)
                    .frame(maxWidth: .infinity, alignment: .leading)

                FormField(
                    label: String(localized: "onboarding_profile_name", bundle: .module),
                    text: $model.fullName,
                    kind: .personName
                )

                VStack(alignment: .leading, spacing: Spacing.x1) {
                    Text("onboarding_profile_role", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)

                    Picker(
                        String(localized: "onboarding_profile_role", bundle: .module),
                        selection: $model.professionalRole
                    ) {
                        // Empty tag so nothing is chosen by accident — the web's select has the
                        // same disabled placeholder, and a defaulted role is a wrong role.
                        Text(verbatim: "—").tag(String())
                        ForEach(ProfessionalRole.all, id: \.self) { role in
                            Text(verbatim: role).tag(role)
                        }
                    }
                    .pickerStyle(.menu)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(Spacing.x2)
                    .background(Color.brandSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
                }

                FormField(
                    label: String(localized: "onboarding_profile_phone", bundle: .module),
                    text: $model.phone,
                    kind: .phone
                )

                ErrorBanner(message: model.errorMessage)

                PrimaryButton(
                    title: String(localized: "onboarding_profile_submit", bundle: .module),
                    isLoading: model.isBusy
                ) {
                    Task { await model.submit() }
                }
                .accessibilityIdentifier("profile-submit")
            }
            .padding(Spacing.x5)
        }
        .onChange(of: model.didComplete) { _, done in
            if done { onComplete() }
        }
    }
}
