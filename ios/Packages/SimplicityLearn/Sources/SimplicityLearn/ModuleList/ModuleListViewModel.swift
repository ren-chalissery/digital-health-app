import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class ModuleListViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.learningService) private var learning
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var isLoading = false
    public private(set) var errorMessage: String?
    public private(set) var modules: [AssignedModuleResponse] = []
    public var search: String = .empty

    /// Diacritic-insensitive so that searching "Maori" finds "Māori". The alternative is a
    /// clinician concluding a module they were assigned does not exist.
    public var visible: [AssignedModuleResponse] {
        let query = search.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return modules }

        return modules.filter { module in
            let haystack = [module.title, module.summary].compactMap { $0 }.joined(separator: " ")
            return haystack.range(
                of: query,
                options: [.caseInsensitive, .diacriticInsensitive]
            ) != nil
        }
    }

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func load() async {
        guard let orgId = await session.current?.activeOrganisationId else {
            // Somebody in no organisation has nothing assigned by definition. Asking anyway would
            // fail, and the failure would read as though something were broken.
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            modules = try await learning.assignedModules(orgId: orgId)
        } catch {
            errorMessage = String(localized: "learn_load_failed", bundle: .module)
        }
    }
}
