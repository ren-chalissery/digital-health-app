import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class TeamsViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.organisationService) private var organisations
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var teams: [TeamResponse] = []
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    public var newTeamName: String = .empty
    public var newTeamDescription: String = .empty

    public var canCreate: Bool {
        !newTeamName.trimmingCharacters(in: .whitespaces).isEmpty && !isBusy
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
            teams = try await organisations.teams(orgId: orgId)
        } catch {
            errorMessage = String(localized: "teams_load_failed", bundle: .module)
        }
    }

    public func create() async {
        guard canCreate, let orgId else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            let team = try await organisations.createTeam(
                orgId: orgId,
                name: newTeamName,
                description: newTeamDescription
            )
            teams.append(team)
            newTeamName = .empty
            newTeamDescription = .empty
        } catch {
            // The fields keep their text so the typing is not lost.
            errorMessage = String(localized: "teams_create_failed", bundle: .module)
        }
    }

    public func delete(_ team: TeamResponse) async {
        guard let orgId, let teamId = team.id, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.deleteTeam(orgId: orgId, teamId: teamId)
            teams.removeAll { $0.id == teamId }
        } catch {
            errorMessage = String(localized: "teams_delete_failed", bundle: .module)
        }
    }
}
