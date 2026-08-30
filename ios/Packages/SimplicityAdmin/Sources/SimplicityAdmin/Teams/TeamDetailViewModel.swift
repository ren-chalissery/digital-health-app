import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class TeamDetailViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.organisationService) private var organisations
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let teamId: UUID
    public let teamName: String

    public private(set) var members: [TeamMemberDetailResponse] = []
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    private var allMembers: [OrgMemberResponse] = []
    private var orgId: UUID?

    /// The organisation's members who are not already in this team. Offering somebody twice
    /// invites a confusing server error.
    public var candidates: [OrgMemberResponse] {
        let present = Set(members.compactMap(\.userId))
        return allMembers.filter { member in
            guard let userId = member.userId else { return false }
            return !present.contains(userId)
        }
    }

    // MARK: Init

    public init(teamId: UUID, teamName: String) {
        self.teamId = teamId
        self.teamName = teamName
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
            members = try await organisations.teamMembers(orgId: orgId, teamId: teamId)
            allMembers = try await organisations.members(orgId: orgId)
        } catch {
            errorMessage = String(localized: "team_load_failed", bundle: .module)
        }
    }

    public func add(_ member: OrgMemberResponse, as role: AddTeamMemberRequest.TeamRole) async {
        guard let orgId, let userId = member.userId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.addTeamMember(
                orgId: orgId, teamId: teamId, userId: userId, role: role
            )
            members = try await organisations.teamMembers(orgId: orgId, teamId: teamId)
        } catch {
            errorMessage = String(localized: "team_add_failed", bundle: .module)
        }
    }

    public func remove(_ member: TeamMemberDetailResponse) async {
        guard let orgId, let userId = member.userId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.removeTeamMember(
                orgId: orgId, teamId: teamId, userId: userId
            )
            members.removeAll { $0.userId == userId }
        } catch {
            errorMessage = String(localized: "team_remove_failed", bundle: .module)
        }
    }
}
