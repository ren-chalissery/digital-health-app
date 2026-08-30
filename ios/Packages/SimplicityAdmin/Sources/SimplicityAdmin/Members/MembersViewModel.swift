import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class MembersViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.organisationService) private var organisations
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var members: [OrgMemberResponse] = []
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    private var currentUserId: UUID?
    private var orgId: UUID?

    // MARK: Init

    public init() {}

    // MARK: Functions

    /// The row for yourself offers no role picker and no remove. Removing yourself is *leaving*,
    /// which is a different endpoint with a different consequence, and demoting yourself could
    /// leave the organisation with no administrator.
    public func isSelf(_ member: OrgMemberResponse) -> Bool {
        member.userId != nil && member.userId == currentUserId
    }

    public func load() async {
        let user = await session.current
        currentUserId = user?.id
        orgId = user?.activeOrganisationId
        guard let orgId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            members = try await organisations.members(orgId: orgId)
        } catch {
            errorMessage = String(localized: "members_load_failed", bundle: .module)
        }
    }

    public func changeRole(
        _ member: OrgMemberResponse,
        to role: ChangeOrgRoleRequest.OrgRole
    ) async {
        guard let orgId, let userId = member.userId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            let updated = try await organisations.changeRole(
                orgId: orgId, userId: userId, role: role
            )
            // Replaced with the server's answer rather than mutated locally, so the list never
            // shows a role the server did not agree to.
            if let index = members.firstIndex(where: { $0.userId == userId }) {
                members[index] = updated
            }
        } catch {
            errorMessage = String(localized: "members_role_failed", bundle: .module)
        }
    }

    public func remove(_ member: OrgMemberResponse) async {
        guard let orgId, let userId = member.userId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.removeMember(orgId: orgId, userId: userId)
            members.removeAll { $0.userId == userId }
        } catch {
            // The member stays in the list. A list that lies about who has access is worse than
            // an error message.
            errorMessage = String(localized: "members_remove_failed", bundle: .module)
        }
    }
}
