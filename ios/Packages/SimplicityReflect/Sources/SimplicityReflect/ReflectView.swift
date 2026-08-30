import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct ReflectView: View {

    // MARK: Properties

    @State private var model = ReflectViewModel()
    @State private var pendingDeletion: ReflectionResponse?

    // MARK: Init

    public init() {}

    // MARK: SwiftUI

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.x5) {
                ReflectionEditor(
                    title: $model.title,
                    text: $model.body,
                    warnings: model.warnings,
                    isSaving: model.isSaving,
                    isEditing: model.isEditing,
                    canSave: model.canSave,
                    onSave: { Task { await model.save() } },
                    onCancel: { model.clear() }
                )

                ErrorBanner(message: model.errorMessage)

                entries
            }
            .padding(Spacing.x5)
        }
        .navigationTitle(Text("reflect_title", bundle: .module))
        .searchable(text: $model.query, prompt: Text("reflect_search", bundle: .module))
        .onSubmit(of: .search) { Task { await model.search() } }
        .refreshable { await model.load() }
        .task { await model.load() }
        .confirmationDialog(
            Text("reflect_delete_confirm_title", bundle: .module),
            isPresented: .constant(pendingDeletion != nil),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { entry in
            Button(role: .destructive) {
                let target = entry
                pendingDeletion = nil
                Task { await model.delete(target) }
            } label: {
                Text("reflect_delete", bundle: .module)
            }
            Button(role: .cancel) { pendingDeletion = nil } label: {
                Text("reflect_cancel", bundle: .module)
            }
        } message: { _ in
            Text("reflect_delete_confirm_body", bundle: .module)
        }
    }

    // MARK: Private

    @ViewBuilder
    private var entries: some View {
        if model.isLoading, model.entries.isEmpty {
            ProgressView().frame(maxWidth: .infinity)
        } else if model.entries.isEmpty, !model.query.isEmpty {
            Text("reflect_no_matches", bundle: .module)
                .font(.brandBody)
                .foregroundStyle(Color.brandTextSecondary)
        } else if model.entries.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.x2) {
                Text("reflect_empty_title", bundle: .module)
                    .font(.brandBody.weight(.semibold))
                Text("reflect_empty_body", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(spacing: Spacing.x3) {
                ForEach(model.entries, id: \.id) { entry in
                    row(entry)
                }
            }
        }
    }

    private func row(_ entry: ReflectionResponse) -> some View {
        Button {
            model.edit(entry)
        } label: {
            VStack(alignment: .leading, spacing: Spacing.x1) {
                if let title = entry.title, !title.isEmpty {
                    Text(verbatim: title)
                        .font(.brandBody.weight(.semibold))
                        .foregroundStyle(Color.brandTextPrimary)
                }

                Text(verbatim: entry.body ?? "")
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
                    .lineLimit(3)

                if let created = entry.createdAt {
                    Text(created, format: .dateTime.day().month(.abbreviated).year())
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.x3)
            .background(Color.brandSurface)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        // A confirmation before deleting: an accidental swipe destroying a private reflection is
        // unrecoverable, because nothing is kept anywhere else.
        .swipeActions {
            Button(role: .destructive) {
                pendingDeletion = entry
            } label: {
                Text("reflect_delete", bundle: .module)
            }
        }
    }
}
