import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct DashboardView: View {

    // MARK: Properties

    @State private var model = DashboardViewModel()
    private let onOpen: (UUID) -> Void

    // MARK: Init

    public init(onOpen: @escaping (UUID) -> Void) {
        self.onOpen = onOpen
    }

    // MARK: SwiftUI

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.x5) {
                greeting

                ErrorBanner(message: model.errorMessage)

                if let next = model.next {
                    nextCard(next)
                }

                if !model.assigned.isEmpty {
                    Text(
                        String(
                            format: String(localized: "dashboard_completed_count", bundle: .module),
                            model.assigned.count - model.outstanding.count
                        )
                    )
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
                }
            }
            .padding(Spacing.x5)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle(Text("dashboard_title", bundle: .module))
        .refreshable { await model.load() }
        .task { await model.load() }
    }

    // MARK: Private

    private var greeting: some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            Text(verbatim: greetingText)
                .font(.brandTitle)

            Text(verbatim: model.lede)
                .font(.brandBody)
                .foregroundStyle(Color.brandTextSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    /// Falls back to a bare greeting rather than "Kia ora, " with nothing after the comma.
    private var greetingText: String {
        model.firstName.isEmpty
            ? String(localized: "dashboard_greeting_anonymous", bundle: .module)
            : String(
                format: String(localized: "dashboard_greeting", bundle: .module),
                model.firstName
            )
    }

    private func nextCard(_ module: AssignedModuleResponse) -> some View {
        Button {
            if let moduleId = module.moduleId { onOpen(moduleId) }
        } label: {
            VStack(alignment: .leading, spacing: Spacing.x2) {
                Text("dashboard_next_title", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)

                Text(verbatim: module.title ?? "")
                    .font(.brandBody.weight(.semibold))
                    .foregroundStyle(Color.brandTextPrimary)

                Text(
                    String(
                        format: String(localized: "learn_section_progress", bundle: .module),
                        module.completedSectionCount ?? 0,
                        module.sectionCount ?? 0
                    )
                )
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
            }
            .padding(Spacing.x4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.brandSurface)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.x3))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
