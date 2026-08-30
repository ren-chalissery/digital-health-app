import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct ModuleEditorView: View {

    // MARK: Properties

    @State private var model: ModuleEditorViewModel
    private let title: String

    // MARK: Init

    public init(moduleId: UUID, title: String) {
        self._model = State(initialValue: ModuleEditorViewModel(moduleId: moduleId))
        self.title = title
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            if !model.isEditable {
                Section {
                    Text("authoring_open_draft_hint", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)

                    Button {
                        Task { await model.openDraft() }
                    } label: {
                        Text("authoring_open_draft", bundle: .module)
                    }
                    .accessibilityIdentifier("open-draft")
                }
            }

            ErrorBanner(message: model.errorMessage)

            // In the list rather than the toolbar: `topBarLeading` does not exist on macOS, where
            // this package still has to build, and unsaved work deserves more room than a
            // toolbar caption anyway.
            if model.isDirty {
                Text("authoring_unsaved", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            Section(String(localized: "authoring_sections", bundle: .module)) {
                // By value, not by binding: the view model owns every mutation so that `isDirty`
                // and the ordering stay its business rather than the view's.
                ForEach(model.sections) { section in
                    sectionEditor(section)
                }
                .onMove { model.moveSection(from: $0, to: $1) }
                .onDelete { indexes in
                    indexes.map { model.sections[$0].id }.forEach(model.deleteSection)
                }

                if model.isEditable {
                    Button {
                        model.addSection()
                    } label: {
                        Label {
                            Text("authoring_add_section", bundle: .module)
                        } icon: {
                            Image(systemName: "plus.circle")
                        }
                    }
                    .accessibilityIdentifier("add-section")
                }
            }
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if model.isEditable {
                    Button {
                        Task { await model.save() }
                    } label: {
                        Text(
                            model.isSaving
                                ? "authoring_saving"
                                : "authoring_save",
                            bundle: .module
                        )
                    }
                    .disabled(model.isSaving)
                    .accessibilityIdentifier("save-sections")
                }
            }
        }
        .task { await model.load() }
    }

    // MARK: Private

    private func sectionEditor(_ section: DraftSection) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            TextField(
                String(localized: "authoring_section_title", bundle: .module),
                text: Binding(
                    get: { section.title },
                    set: { model.updateSection(id: section.id, title: $0) }
                )
            )
            .font(.brandBody.weight(.semibold))

            TextField(
                String(localized: "authoring_section_body", bundle: .module),
                text: Binding(
                    get: { section.body },
                    set: { model.updateSection(id: section.id, body: $0) }
                ),
                axis: .vertical
            )
            .font(.brandBody)
            .lineLimit(3...12)
        }
        .disabled(!model.isEditable)
        .padding(.vertical, Spacing.x1)
    }
}
