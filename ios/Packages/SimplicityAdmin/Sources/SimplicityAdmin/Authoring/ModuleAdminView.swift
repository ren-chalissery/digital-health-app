import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct ModuleAdminView: View {

    // MARK: Properties

    @State private var model = ModuleAdminViewModel()
    @State private var pendingArchive: ModuleSummaryResponse?
    private let onOpen: (AdminDestination) -> Void

    // MARK: Init

    public init(onOpen: @escaping (AdminDestination) -> Void) {
        self.onOpen = onOpen
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            Section {
                TextField(
                    String(localized: "authoring_new_title", bundle: .module),
                    text: $model.newTitle
                )
                TextField(
                    String(localized: "authoring_new_summary", bundle: .module),
                    text: $model.newSummary
                )
                Button {
                    Task { await model.create() }
                } label: {
                    Text("authoring_create", bundle: .module)
                }
                .disabled(!model.canCreate)
                .accessibilityIdentifier("module-create")
            }

            ErrorBanner(message: model.errorMessage)

            if model.modules.isEmpty, !model.isLoading {
                Text("authoring_empty", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            ForEach(model.modules, id: \.moduleId) { module in
                row(module)
            }
        }
        .navigationTitle(Text("authoring_title", bundle: .module))
        .refreshable { await model.load() }
        .task { await model.load() }
        .confirmationDialog(
            Text(verbatim: pendingArchive?.title ?? ""),
            isPresented: .constant(pendingArchive != nil),
            titleVisibility: .visible,
            presenting: pendingArchive
        ) { module in
            Button(role: .destructive) {
                let target = module
                pendingArchive = nil
                Task { await model.archive(target) }
            } label: {
                Text("authoring_archive", bundle: .module)
            }
            Button(role: .cancel) { pendingArchive = nil } label: { Text(verbatim: "Cancel") }
        }
    }

    // MARK: Private

    private func row(_ module: ModuleSummaryResponse) -> some View {
        Button {
            if let moduleId = module.moduleId {
                onOpen(.module(id: moduleId, title: module.title ?? ""))
            }
        } label: {
            VStack(alignment: .leading, spacing: Spacing.x1) {
                Text(verbatim: module.title ?? "")
                    .font(.brandBody.weight(.semibold))
                    .foregroundStyle(Color.brandTextPrimary)

                if let summary = module.summary, !summary.isEmpty {
                    Text(verbatim: summary)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                }

                Text(verbatim: stateLabel(module))
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .accessibilityElement(children: .combine)
        }
        .buttonStyle(.plain)
        .swipeActions {
            Button(role: .destructive) {
                pendingArchive = module
            } label: {
                Text("authoring_archive", bundle: .module)
            }
        }
    }

    /// Three distinct states, not two badges. "Not published yet" and "published, with edits a
    /// learner cannot see yet" mean very different things.
    private func stateLabel(_ module: ModuleSummaryResponse) -> String {
        if module.hasUnpublishedChanges {
            return String(localized: "authoring_state_unpublished_changes", bundle: .module)
        }
        if module.isPublished {
            return String(localized: "authoring_state_published", bundle: .module)
        }
        return String(localized: "authoring_state_draft", bundle: .module)
    }
}
