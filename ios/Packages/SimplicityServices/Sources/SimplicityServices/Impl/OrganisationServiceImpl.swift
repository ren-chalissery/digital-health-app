import Foundation
import SimplicityApi

public final class OrganisationServiceImpl: OrganisationService {

    // MARK: Types

    public typealias Members = @Sendable (UUID) async throws -> [OrgMemberResponse]
    public typealias ChangeRole = @Sendable (UUID, UUID, ChangeOrgRoleRequest) async throws
        -> OrgMemberResponse
    public typealias RemoveMember = @Sendable (UUID, UUID) async throws -> Void
    public typealias Leave = @Sendable (UUID) async throws -> Void
    public typealias Teams = @Sendable (UUID) async throws -> [TeamResponse]
    public typealias CreateTeam = @Sendable (UUID, CreateTeamRequest) async throws -> TeamResponse
    public typealias DeleteTeam = @Sendable (UUID, UUID) async throws -> Void
    public typealias TeamMembers = @Sendable (UUID, UUID) async throws
        -> [TeamMemberDetailResponse]
    public typealias AddTeamMember = @Sendable (UUID, UUID, AddTeamMemberRequest) async throws
        -> Void
    public typealias RemoveTeamMember = @Sendable (UUID, UUID, UUID) async throws -> Void
    public typealias Invitations = @Sendable (UUID) async throws -> [InvitationResponse]
    public typealias Invite = @Sendable (UUID, CreateInvitationRequest) async throws
        -> InvitationResponse
    public typealias Revoke = @Sendable (UUID, UUID) async throws -> Void

    // MARK: Properties

    private let membersCall: Members
    private let changeRoleCall: ChangeRole
    private let removeMemberCall: RemoveMember
    private let leaveCall: Leave
    private let teamsCall: Teams
    private let createTeamCall: CreateTeam
    private let deleteTeamCall: DeleteTeam
    private let teamMembersCall: TeamMembers
    private let addTeamMemberCall: AddTeamMember
    private let removeTeamMemberCall: RemoveTeamMember
    private let invitationsCall: Invitations
    private let inviteCall: Invite
    private let revokeCall: Revoke

    // MARK: Init

    // swiftlint:disable function_default_parameter_at_end
    public init(
        members: @escaping Members = { orgId in
            try await OrganisationsAPI.listOrganisationMembers(orgId: orgId)
        },
        changeRole: @escaping ChangeRole = { orgId, userId, request in
            try await OrganisationsAPI.changeOrganisationRole(
                orgId: orgId, userId: userId, changeOrgRoleRequest: request
            )
        },
        removeMember: @escaping RemoveMember = { orgId, userId in
            try await OrganisationsAPI.removeOrganisationMember(orgId: orgId, userId: userId)
        },
        leave: @escaping Leave = { orgId in
            try await OrganisationsAPI.leaveOrganisation(orgId: orgId)
        },
        teams: @escaping Teams = { orgId in
            try await TeamsAPI.listTeams(orgId: orgId)
        },
        createTeam: @escaping CreateTeam = { orgId, request in
            try await TeamsAPI.createTeam(orgId: orgId, createTeamRequest: request)
        },
        deleteTeam: @escaping DeleteTeam = { orgId, teamId in
            try await TeamsAPI.deleteTeam(orgId: orgId, teamId: teamId)
        },
        teamMembers: @escaping TeamMembers = { orgId, teamId in
            try await TeamsAPI.listTeamMembers(orgId: orgId, teamId: teamId)
        },
        addTeamMember: @escaping AddTeamMember = { orgId, teamId, request in
            try await TeamsAPI.addTeamMember(
                orgId: orgId, teamId: teamId, addTeamMemberRequest: request
            )
        },
        removeTeamMember: @escaping RemoveTeamMember = { orgId, teamId, userId in
            try await TeamsAPI.removeTeamMember(orgId: orgId, teamId: teamId, userId: userId)
        },
        invitations: @escaping Invitations = { orgId in
            try await InvitationsAPI.listInvitations(orgId: orgId)
        },
        invite: @escaping Invite = { orgId, request in
            try await InvitationsAPI.createInvitation(
                orgId: orgId, createInvitationRequest: request
            )
        },
        revoke: @escaping Revoke = { orgId, invitationId in
            try await InvitationsAPI.revokeInvitation(orgId: orgId, invitationId: invitationId)
        }
    ) {
        self.membersCall = members
        self.changeRoleCall = changeRole
        self.removeMemberCall = removeMember
        self.leaveCall = leave
        self.teamsCall = teams
        self.createTeamCall = createTeam
        self.deleteTeamCall = deleteTeam
        self.teamMembersCall = teamMembers
        self.addTeamMemberCall = addTeamMember
        self.removeTeamMemberCall = removeTeamMember
        self.invitationsCall = invitations
        self.inviteCall = invite
        self.revokeCall = revoke
    }
    // swiftlint:enable function_default_parameter_at_end

    // MARK: Members

    public func members(orgId: UUID) async throws -> [OrgMemberResponse] {
        try await membersCall(orgId)
    }

    @discardableResult
    public func changeRole(
        orgId: UUID,
        userId: UUID,
        role: ChangeOrgRoleRequest.OrgRole
    ) async throws -> OrgMemberResponse {
        try await changeRoleCall(orgId, userId, ChangeOrgRoleRequest(orgRole: role))
    }

    public func removeMember(orgId: UUID, userId: UUID) async throws {
        try await removeMemberCall(orgId, userId)
    }

    public func leave(orgId: UUID) async throws {
        try await leaveCall(orgId)
    }

    // MARK: Teams

    public func teams(orgId: UUID) async throws -> [TeamResponse] {
        try await teamsCall(orgId)
    }

    /// An empty description is sent as nil. "" and absent are different to the server, and an
    /// empty string would render as a blank line under the team's name.
    public func createTeam(
        orgId: UUID,
        name: String,
        description: String?
    ) async throws -> TeamResponse {
        let trimmed = description?.trimmingCharacters(in: .whitespacesAndNewlines)
        return try await createTeamCall(
            orgId,
            CreateTeamRequest(
                description: trimmed?.isEmpty == false ? trimmed : nil,
                name: name.trimmingCharacters(in: .whitespaces)
            )
        )
    }

    public func deleteTeam(orgId: UUID, teamId: UUID) async throws {
        try await deleteTeamCall(orgId, teamId)
    }

    public func teamMembers(orgId: UUID, teamId: UUID) async throws -> [TeamMemberDetailResponse] {
        try await teamMembersCall(orgId, teamId)
    }

    public func addTeamMember(
        orgId: UUID,
        teamId: UUID,
        userId: UUID,
        role: AddTeamMemberRequest.TeamRole
    ) async throws {
        try await addTeamMemberCall(
            orgId, teamId, AddTeamMemberRequest(teamRole: role, userId: userId)
        )
    }

    public func removeTeamMember(orgId: UUID, teamId: UUID, userId: UUID) async throws {
        try await removeTeamMemberCall(orgId, teamId, userId)
    }

    // MARK: Invitations

    public func invitations(orgId: UUID) async throws -> [InvitationResponse] {
        try await invitationsCall(orgId)
    }

    /// The address is normalised here so an invitation to "Ana@Example.com " reaches the same
    /// person as "ana@example.com", matching how the server stores addresses.
    ///
    /// A team role without a team is meaningless and the server refuses the pair, so choosing no
    /// team drops both rather than sending a dangling role.
    @discardableResult
    public func invite(
        orgId: UUID,
        email: String,
        orgRole: CreateInvitationRequest.OrgRole,
        teamId: UUID?,
        teamRole: CreateInvitationRequest.TeamRole?
    ) async throws -> InvitationResponse {
        try await inviteCall(
            orgId,
            CreateInvitationRequest(
                email: email.trimmingCharacters(in: .whitespaces).lowercased(),
                orgRole: orgRole,
                teamId: teamId,
                teamRole: teamId == nil ? nil : teamRole
            )
        )
    }

    public func revokeInvitation(orgId: UUID, invitationId: UUID) async throws {
        try await revokeCall(orgId, invitationId)
    }
}
