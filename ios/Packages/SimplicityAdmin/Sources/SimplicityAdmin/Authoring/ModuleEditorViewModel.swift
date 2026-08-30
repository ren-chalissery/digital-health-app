import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

/// A section being edited.
///
/// Identity is client-side because `PUT /draft/sections` replaces the whole list: a section not
/// yet saved has no server id, and reordering must not lose track of which row is which.
public struct DraftSection: Identifiable, Equatable, Sendable {
    public let id: UUID
    public var title: String
    public var body: String
    public var mediaAssetId: UUID?

    public init(id: UUID = UUID(), title: String = "", body: String = "", mediaAssetId: UUID? = nil) {
        self.id = id
        self.title = title
        self.body = body
        self.mediaAssetId = mediaAssetId
    }
}

@Observable
@MainActor
public final class ModuleEditorViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authoringService) private var authoring
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let moduleId: UUID

    public private(set) var module: AuthoredModuleResponse?
    public private(set) var sections: [DraftSection] = []
    public private(set) var isLoading = false
    public private(set) var isSaving = false
    public private(set) var isDirty = false
    public private(set) var errorMessage: String?

    public var isEditable: Bool {
        module?.hasDraft == true
    }

    private var orgId: UUID?

    // MARK: Init

    public init(moduleId: UUID) {
        self.moduleId = moduleId
    }

    // MARK: Functions

    public func load() async {
        orgId = await session.current?.activeOrganisationId
        guard let orgId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let loaded = try await authoring.module(orgId: orgId, moduleId: moduleId)
            module = loaded
            adopt(loaded)
        } catch {
            errorMessage = String(localized: "authoring_load_failed", bundle: .module)
        }
    }

    /// A published version is immutable, so editing starts by opening a draft of it.
    public func openDraft() async {
        guard let orgId, !isSaving else { return }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            let opened = try await authoring.openDraft(orgId: orgId, moduleId: moduleId)
            module = opened
            adopt(opened)
        } catch {
            errorMessage = String(localized: "authoring_draft_failed", bundle: .module)
        }
    }

    public func addSection() {
        sections.append(DraftSection())
        isDirty = true
    }

    public func updateSection(id: UUID, title: String? = nil, body: String? = nil) {
        guard let index = sections.firstIndex(where: { $0.id == id }) else { return }
        if let title { sections[index].title = title }
        if let body { sections[index].body = body }
        isDirty = true
    }

    public func attach(assetId: UUID?, to id: UUID) {
        guard let index = sections.firstIndex(where: { $0.id == id }) else { return }
        sections[index].mediaAssetId = assetId
        isDirty = true
    }

    public func moveSection(from source: IndexSet, to destination: Int) {
        sections.move(fromOffsets: source, toOffset: destination)
        isDirty = true
    }

    public func deleteSection(id: UUID) {
        sections.removeAll { $0.id == id }
        isDirty = true
    }

    public func save() async {
        guard let orgId, isEditable, !isSaving else { return }

        // A blank heading makes the reader unnavigable, and the server would refuse it anyway.
        if sections.contains(where: { $0.title.trimmingCharacters(in: .whitespaces).isEmpty }) {
            errorMessage = String(localized: "authoring_section_needs_title", bundle: .module)
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        // Every section, in order — the endpoint replaces the list rather than patching it.
        let inputs = sections.map {
            SectionInput(body: $0.body, mediaAssetId: $0.mediaAssetId, title: $0.title)
        }

        do {
            let updated = try await authoring.replaceSections(
                orgId: orgId, moduleId: moduleId, sections: inputs
            )
            module = updated
            isDirty = false
        } catch {
            // Left dirty, so nothing suggests the work was stored.
            errorMessage = String(localized: "authoring_save_failed", bundle: .module)
        }
    }

    // MARK: Private

    /// Draft first, then published. Editing should start from what is live rather than from
    /// nothing when a module has been published but not yet reopened.
    private func adopt(_ module: AuthoredModuleResponse) {
        let version = module.draft ?? module.published
        sections = (version?.sections ?? []).map {
            DraftSection(
                title: $0.title ?? .empty,
                body: $0.body ?? .empty,
                mediaAssetId: $0.mediaAssetId
            )
        }
        isDirty = false
    }
}
