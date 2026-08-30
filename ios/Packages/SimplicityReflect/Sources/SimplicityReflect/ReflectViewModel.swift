import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class ReflectViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.reflectionService) private var reflections

    // MARK: Properties

    public private(set) var isLoading = false
    public private(set) var isSaving = false
    public private(set) var errorMessage: String?
    public private(set) var entries: [ReflectionResponse] = []
    public private(set) var editingId: UUID?

    public var query: String = .empty
    public var title: String = .empty
    public var body: String = .empty

    /// Recomputed with every keystroke through `@Observable`, because the point is to change what
    /// gets written — which is too late once it has been.
    ///
    /// The title is included: a name in a title is still a name.
    public var warnings: [IdentifierWarning] {
        Identifiers.find(in: title + " " + body)
    }

    public var canSave: Bool {
        !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving
    }

    public var isEditing: Bool {
        editingId != nil
    }

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            entries = try await reflections.list(query: nil)
        } catch {
            errorMessage = String(localized: "reflect_load_failed", bundle: .module)
        }
    }

    public func search() async {
        errorMessage = nil
        do {
            entries = try await reflections.list(query: query)
        } catch {
            errorMessage = String(localized: "reflect_load_failed", bundle: .module)
        }
    }

    public func save() async {
        guard canSave else { return }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        let trimmedTitle = title.trimmingCharacters(in: .whitespaces)

        do {
            if let editingId {
                _ = try await reflections.edit(
                    id: editingId,
                    title: trimmedTitle.isEmpty ? nil : trimmedTitle,
                    body: body
                )
            } else {
                _ = try await reflections.write(
                    title: trimmedTitle.isEmpty ? nil : trimmedTitle,
                    body: body
                )
            }
            clear()
            await load()
        } catch {
            // The fields are deliberately not cleared. This text exists nowhere else — no draft,
            // no cache, nothing on disk — so clearing it here would destroy what was written.
            errorMessage = String(localized: "reflect_save_failed", bundle: .module)
        }
    }

    public func edit(_ entry: ReflectionResponse) {
        editingId = entry.id
        title = entry.title ?? .empty
        body = entry.body ?? .empty
    }

    public func clear() {
        editingId = nil
        title = .empty
        body = .empty
    }

    public func delete(_ entry: ReflectionResponse) async {
        guard let id = entry.id else { return }

        errorMessage = nil
        do {
            try await reflections.delete(id: id)
            entries.removeAll { $0.id == id }
            if editingId == id { clear() }
        } catch {
            errorMessage = String(localized: "reflect_delete_failed", bundle: .module)
        }
    }
}
