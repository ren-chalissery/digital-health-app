import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct ModuleListView: View {

    // MARK: Properties

    @State private var model = ModuleListViewModel()
    private let onOpen: (UUID) -> Void

    // MARK: Init

    public init(onOpen: @escaping (UUID) -> Void) {
        self.onOpen = onOpen
    }

    // MARK: SwiftUI

    public var body: some View {
        content
            .navigationTitle(Text("learn_title", bundle: .module))
            .searchable(
                text: $model.search,
                prompt: Text("learn_search", bundle: .module)
            )
            .refreshable { await model.load() }
            .task { await model.load() }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading, model.modules.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let message = model.errorMessage {
            // A failure is its own state, never an empty list — those mean different things and a
            // clinician acts differently on each.
            ContentUnavailableView {
                Label(message, systemImage: "wifi.exclamationmark")
            } actions: {
                Button("Try again") { Task { await model.load() } }
            }
        } else if model.modules.isEmpty {
            ContentUnavailableView(
                String(localized: "learn_empty_title", bundle: .module),
                systemImage: "tray",
                description: Text("learn_empty_body", bundle: .module)
            )
        } else if model.visible.isEmpty {
            ContentUnavailableView(
                String(localized: "learn_no_matches", bundle: .module),
                systemImage: "magnifyingglass"
            )
        } else {
            List(model.visible, id: \.moduleId) { module in
                Button {
                    if let moduleId = module.moduleId { onOpen(moduleId) }
                } label: {
                    ModuleRow(module: module)
                }
                .buttonStyle(.plain)
            }
            .listStyle(.plain)
        }
    }
}

private struct ModuleRow: View {

    let module: AssignedModuleResponse

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            Text(verbatim: module.title ?? "")
                .font(.brandBody.weight(.semibold))
                .foregroundStyle(Color.brandTextPrimary)

            if let summary = module.summary, !summary.isEmpty {
                Text(verbatim: summary)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            HStack(spacing: Spacing.x3) {
                if let status = module.status {
                    Text(verbatim: status.label)
                        .font(.brandCaption)
                        .padding(.horizontal, Spacing.x2)
                        .padding(.vertical, Spacing.x1)
                        .background(status == .completed ? Color.brandPrimary.opacity(0.12) : Color.brandSurface)
                        .clipShape(Capsule())
                }

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
        }
        .padding(.vertical, Spacing.x1)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        // One announcement per module rather than four unrelated fragments.
        .accessibilityElement(children: .combine)
    }
}
