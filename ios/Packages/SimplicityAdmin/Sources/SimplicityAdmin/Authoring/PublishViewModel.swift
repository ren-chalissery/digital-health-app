import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class PublishViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authoringService) private var authoring
    @ObservationIgnored @Injected(\.organisationService) private var organisations
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let moduleId: UUID

    public private(set) var teams: [TeamResponse] = []
    public private(set) var module: AuthoredModuleResponse?
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?
    public private(set) var didPublish = false

    public var selectedTeamIds: Set<UUID> = []

    /// Whether everybody who already finished this module has to do it again. A corrected typo
    /// should not; changed guidance should.
    public var supersedesCompletions = false

    private var orgId: UUID?

    public var hasSections: Bool {
        let version = module?.draft ?? module?.published
        return !(version?.sections ?? []).isEmpty
    }

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
            // Pre-selected from what is already assigned, so saving without touching anything
            // changes nothing.
            selectedTeamIds = Set(loaded.assignedTeamIds ?? [])
            teams = try await organisations.teams(orgId: orgId)
        } catch {
            errorMessage = String(localized: "authoring_load_failed", bundle: .module)
        }
    }

    public func toggle(_ teamId: UUID) {
        if selectedTeamIds.contains(teamId) {
            selectedTeamIds.remove(teamId)
        } else {
            selectedTeamIds.insert(teamId)
        }
    }

    public func assign() async {
        guard let orgId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            // An empty array is how a module is unassigned, so it is sent rather than skipped.
            module = try await authoring.assignTeams(
                orgId: orgId, moduleId: moduleId, teamIds: Array(selectedTeamIds)
            )
        } catch {
            errorMessage = String(localized: "publish_assign_failed", bundle: .module)
        }
    }

    public func publish() async {
        guard let orgId, !isBusy else { return }

        // The server refuses this too; a round trip to learn it is wasted.
        guard hasSections else {
            errorMessage = String(localized: "publish_needs_sections", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            module = try await authoring.publish(
                orgId: orgId,
                moduleId: moduleId,
                supersedesCompletions: supersedesCompletions
            )
            didPublish = true
        } catch {
            errorMessage = String(localized: "publish_failed", bundle: .module)
        }
    }
}
