import Foundation
import Mockable
import SimplicityApi

/// Who is in an organisation: its members, its teams, and the invitations not yet accepted.
///
/// Every call here is authorised by the server. The app hides controls a member cannot use, but
/// that is presentation — a demoted administrator with the screen still open will get a 403, and
/// that has to read as an ordinary failure rather than a crash.
@Mockable
public protocol OrganisationService: AnyObject, Sendable {

    // MARK: Members

    func members(orgId: UUID) async throws -> [OrgMemberResponse]

    @discardableResult
    func changeRole(
        orgId: UUID,
        userId: UUID,
        role: ChangeOrgRoleRequest.OrgRole
    ) async throws -> OrgMemberResponse

    func removeMember(orgId: UUID, userId: UUID) async throws

    /// Distinct from `removeMember`. Leaving is something you do to yourself, cannot undo without
    /// a fresh invitation, and may be refused outright if you are the last administrator.
    func leave(orgId: UUID) async throws

    // MARK: Teams

    func teams(orgId: UUID) async throws -> [TeamResponse]
    func createTeam(orgId: UUID, name: String, description: String?) async throws -> TeamResponse
    func deleteTeam(orgId: UUID, teamId: UUID) async throws
    func teamMembers(orgId: UUID, teamId: UUID) async throws -> [TeamMemberDetailResponse]

    func addTeamMember(
        orgId: UUID,
        teamId: UUID,
        userId: UUID,
        role: AddTeamMemberRequest.TeamRole
    ) async throws

    func removeTeamMember(orgId: UUID, teamId: UUID, userId: UUID) async throws

    // MARK: Invitations

    func invitations(orgId: UUID) async throws -> [InvitationResponse]

    @discardableResult
    func invite(
        orgId: UUID,
        email: String,
        orgRole: CreateInvitationRequest.OrgRole,
        teamId: UUID?,
        teamRole: CreateInvitationRequest.TeamRole?
    ) async throws -> InvitationResponse

    func revokeInvitation(orgId: UUID, invitationId: UUID) async throws
}
