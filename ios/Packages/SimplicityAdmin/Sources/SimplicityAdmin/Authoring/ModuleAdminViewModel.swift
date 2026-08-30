import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class ModuleAdminViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authoringService) private var authoring
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var modules: [ModuleSummaryResponse] = []
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    public var newTitle: String = .empty
    public var newSummary: String = .empty

    public var canCreate: Bool {
        !newTitle.trimmingCharacters(in: .whitespaces).isEmpty && !isBusy
    }

    private var orgId: UUID?

    // MARK: Init

    public init() {}

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
            modules = try await authoring.modules(orgId: orgId)
        } catch {
            errorMessage = String(localized: "authoring_load_failed", bundle: .module)
        }
    }

    public func create() async {
        guard canCreate, let orgId else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            _ = try await authoring.create(
                orgId: orgId, title: newTitle, summary: newSummary
            )
            newTitle = .empty
            newSummary = .empty
            // Reloaded rather than appended: create returns the full module, and the list holds
            // summaries with counts only the server computes.
            modules = try await authoring.modules(orgId: orgId)
        } catch {
            errorMessage = String(localized: "authoring_create_failed", bundle: .module)
        }
    }

    public func archive(_ module: ModuleSummaryResponse) async {
        guard let orgId, let moduleId = module.moduleId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await authoring.archive(orgId: orgId, moduleId: moduleId)
            modules.removeAll { $0.moduleId == moduleId }
        } catch {
            errorMessage = String(localized: "authoring_archive_failed", bundle: .module)
        }
    }
}
