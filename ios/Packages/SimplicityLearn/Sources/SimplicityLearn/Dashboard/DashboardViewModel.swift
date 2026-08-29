import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

/// Ported from `web/src/app/features/dashboard/dashboard.ts`, wording included, so the same
/// clinician reads the same sentence on both clients.
@Observable
@MainActor
public final class DashboardViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.learningService) private var learning
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var isLoading = false
    public private(set) var errorMessage: String?
    public private(set) var assigned: [AssignedModuleResponse] = []
    public private(set) var firstName: String = .empty

    public var outstanding: [AssignedModuleResponse] {
        assigned.filter(\.isOutstanding)
    }

    /// Whatever is already underway, else the first thing not started. Resuming matters more than
    /// starting: a module is several sections long and nobody finishes one in a sitting.
    public var next: AssignedModuleResponse? {
        outstanding.first { $0.status == .inProgress } ?? outstanding.first
    }

    public var lede: String {
        if isLoading {
            return String(localized: "dashboard_lede_loading", bundle: .module)
        }
        if assigned.isEmpty {
            return String(localized: "dashboard_lede_none_assigned", bundle: .module)
        }
        let count = outstanding.count
        if count == 0 {
            return String(localized: "dashboard_lede_all_done", bundle: .module)
        }
        let format = count == 1
            ? String(localized: "dashboard_lede_outstanding_one", bundle: .module)
            : String(localized: "dashboard_lede_outstanding_many", bundle: .module)
        return String(format: format, count)
    }

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func load() async {
        let user = await session.current
        firstName = (user?.fullName ?? .empty)
            .split(separator: " ")
            .first
            .map(String.init) ?? .empty

        guard let orgId = user?.activeOrganisationId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            assigned = try await learning.assignedModules(orgId: orgId)
        } catch {
            errorMessage = String(localized: "learn_load_failed", bundle: .module)
        }
    }
}
