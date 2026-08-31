package io.simplicity.training.services

import io.simplicity.training.api.apis.InvitationsApi
import io.simplicity.training.api.apis.OrganisationsApi
import io.simplicity.training.api.apis.TeamsApi
import io.simplicity.training.api.models.AddTeamMemberRequest
import io.simplicity.training.api.models.ChangeOrgRoleRequest
import io.simplicity.training.api.models.CreateInvitationRequest
import io.simplicity.training.api.models.CreateTeamRequest
import io.simplicity.training.api.models.InvitationResponse
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.api.models.TeamResponse
import java.util.UUID

/**
 * Administering an organisation: its people, its teams and its invitations.
 *
 * Leaving is separate from removing a member, and the distinction matters. Removing acts on a
 * colleague; leaving acts on yourself, and when the last administrator leaves the server archives
 * the organisation rather than leaving it unadministered.
 */
interface OrganisationService {
    suspend fun members(orgId: UUID): List<OrgMemberResponse>
    suspend fun changeRole(orgId: UUID, userId: UUID, role: ChangeOrgRoleRequest.OrgRole): OrgMemberResponse
    suspend fun removeMember(orgId: UUID, userId: UUID)
    suspend fun leave(orgId: UUID)

    suspend fun teams(orgId: UUID): List<TeamResponse>
    suspend fun createTeam(orgId: UUID, name: String): TeamResponse
    suspend fun addTeamMember(orgId: UUID, teamId: UUID, userId: UUID, role: AddTeamMemberRequest.TeamRole)
    suspend fun removeTeamMember(orgId: UUID, teamId: UUID, userId: UUID)

    suspend fun invitations(orgId: UUID): List<InvitationResponse>
    suspend fun invite(orgId: UUID, email: String, role: CreateInvitationRequest.OrgRole, teamId: UUID?): InvitationResponse
    suspend fun revokeInvitation(orgId: UUID, invitationId: UUID)
}

class OrganisationServiceImpl(
    private val organisations: OrganisationsApi,
    private val teamsApi: TeamsApi,
    private val invitationsApi: InvitationsApi,
) : OrganisationService {

    override suspend fun members(orgId: UUID) = organisations.listOrganisationMembers(orgId).unwrap()

    override suspend fun changeRole(orgId: UUID, userId: UUID, role: ChangeOrgRoleRequest.OrgRole) =
        organisations.changeOrganisationRole(orgId, userId, ChangeOrgRoleRequest(role)).unwrap()

    override suspend fun removeMember(orgId: UUID, userId: UUID) {
        organisations.removeOrganisationMember(orgId, userId).requireSuccess()
    }

    override suspend fun leave(orgId: UUID) {
        organisations.leaveOrganisation(orgId).requireSuccess()
    }

    override suspend fun teams(orgId: UUID) = teamsApi.listTeams(orgId).unwrap()

    override suspend fun createTeam(orgId: UUID, name: String) =
        teamsApi.createTeam(orgId, CreateTeamRequest(name)).unwrap()

    override suspend fun addTeamMember(
        orgId: UUID,
        teamId: UUID,
        userId: UUID,
        role: AddTeamMemberRequest.TeamRole,
    ) {
        teamsApi.addTeamMember(orgId, teamId, AddTeamMemberRequest(role, userId)).requireSuccess()
    }

    override suspend fun removeTeamMember(orgId: UUID, teamId: UUID, userId: UUID) {
        teamsApi.removeTeamMember(orgId, teamId, userId).requireSuccess()
    }

    override suspend fun invitations(orgId: UUID) = invitationsApi.listInvitations(orgId).unwrap()

    override suspend fun invite(
        orgId: UUID,
        email: String,
        role: CreateInvitationRequest.OrgRole,
        teamId: UUID?,
    ) = invitationsApi.createInvitation(
        orgId,
        CreateInvitationRequest(email = email, orgRole = role, teamId = teamId),
    ).unwrap()

    override suspend fun revokeInvitation(orgId: UUID, invitationId: UUID) {
        invitationsApi.revokeInvitation(orgId, invitationId).requireSuccess()
    }
}

/** For the endpoints that answer 204. There is no body to unwrap, only a status to check. */
internal fun <T> retrofit2.Response<T>.requireSuccess() {
    if (!isSuccessful) throw ApiFailure(code(), "Request failed with ${code()}")
}
