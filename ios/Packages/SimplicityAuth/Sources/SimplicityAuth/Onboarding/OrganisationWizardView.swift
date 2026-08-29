import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct OrganisationWizardView: View {

    // MARK: Properties

    @State private var model = OrganisationWizardViewModel()
    private let onComplete: () -> Void

    // MARK: Init

    public init(onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(spacing: Spacing.x4) {
            Text("onboarding_org_title", bundle: .module)
                .font(.brandTitle)
                .frame(maxWidth: .infinity, alignment: .leading)

            FormField(
                label: String(localized: "onboarding_org_name", bundle: .module),
                text: $model.name,
                kind: .plain
            )

            VStack(alignment: .leading, spacing: Spacing.x1) {
                Text("onboarding_org_type", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)

                Picker(
                    String(localized: "onboarding_org_type", bundle: .module),
                    selection: $model.organisationType
                ) {
                    ForEach(CreateOrganisationRequest.OrganisationType.allCases, id: \.self) { type in
                        Text(verbatim: type.label).tag(type)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.x2)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
            }

            ErrorBanner(message: model.errorMessage)

            PrimaryButton(
                title: String(localized: "onboarding_org_submit", bundle: .module),
                isLoading: model.isBusy
            ) {
                Task { await model.submit() }
            }
            .accessibilityIdentifier("organisation-submit")

            Spacer()
        }
        .padding(Spacing.x5)
        .onChange(of: model.didComplete) { _, done in
            if done { onComplete() }
        }
    }
}

private extension CreateOrganisationRequest.OrganisationType {

    /// The generated cases are shouted constants; this is what a person should read.
    var label: String {
        switch self {
        case .hospital: "Hospital"
        case .clinic: "Clinic"
        case .university: "University"
        case .company: "Company"
        case .other: "Other"
        }
    }
}
