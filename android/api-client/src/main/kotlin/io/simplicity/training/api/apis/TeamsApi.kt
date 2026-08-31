package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.AddTeamMemberRequest
import io.simplicity.training.api.models.CreateTeamRequest
import io.simplicity.training.api.models.TeamMemberDetailResponse
import io.simplicity.training.api.models.TeamResponse
import io.simplicity.training.api.models.UpdateTeamRequest

interface TeamsApi {
    /**
     * POST api/v1/orgs/{orgId}/teams/{teamId}/members
     * Add an organisation member to a team
     * 
     * Responses:
     *  - 201: Created
     *
     * @param orgId 
     * @param teamId 
     * @param addTeamMemberRequest 
     * @return [TeamMemberDetailResponse]
     */
    @POST("api/v1/orgs/{orgId}/teams/{teamId}/members")
    suspend fun addTeamMember(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID, @Body addTeamMemberRequest: AddTeamMemberRequest): Response<TeamMemberDetailResponse>

    /**
     * POST api/v1/orgs/{orgId}/teams
     * Create a team
     * Restricted to organisation administrators; team administrators cannot.
     * Responses:
     *  - 201: Created
     *
     * @param orgId 
     * @param createTeamRequest 
     * @return [TeamResponse]
     */
    @POST("api/v1/orgs/{orgId}/teams")
    suspend fun createTeam(@Path("orgId") orgId: java.util.UUID, @Body createTeamRequest: CreateTeamRequest): Response<TeamResponse>

    /**
     * DELETE api/v1/orgs/{orgId}/teams/{teamId}
     * Delete a team
     * Organisation administrators only. A team administrator can manage their team&#39;s membership but cannot remove the team itself.
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param teamId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/teams/{teamId}")
    suspend fun deleteTeam(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID): Response<Unit>

    /**
     * GET api/v1/orgs/{orgId}/teams/{teamId}
     * Fetch one team
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param teamId 
     * @return [TeamResponse]
     */
    @GET("api/v1/orgs/{orgId}/teams/{teamId}")
    suspend fun getTeam(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID): Response<TeamResponse>

    /**
     * GET api/v1/orgs/{orgId}/teams/{teamId}/members
     * List the people in a team
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param teamId 
     * @return [kotlin.collections.List<TeamMemberDetailResponse>]
     */
    @GET("api/v1/orgs/{orgId}/teams/{teamId}/members")
    suspend fun listTeamMembers(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID): Response<kotlin.collections.List<TeamMemberDetailResponse>>

    /**
     * GET api/v1/orgs/{orgId}/teams
     * List the teams in an organisation
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<TeamResponse>]
     */
    @GET("api/v1/orgs/{orgId}/teams")
    suspend fun listTeams(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<TeamResponse>>

    /**
     * DELETE api/v1/orgs/{orgId}/teams/{teamId}/members/{userId}
     * Remove somebody from a team
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param teamId 
     * @param userId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/teams/{teamId}/members/{userId}")
    suspend fun removeTeamMember(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID, @Path("userId") userId: java.util.UUID): Response<Unit>

    /**
     * PATCH api/v1/orgs/{orgId}/teams/{teamId}
     * Rename or redescribe a team
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param teamId 
     * @param updateTeamRequest 
     * @return [TeamResponse]
     */
    @PATCH("api/v1/orgs/{orgId}/teams/{teamId}")
    suspend fun updateTeam(@Path("orgId") orgId: java.util.UUID, @Path("teamId") teamId: java.util.UUID, @Body updateTeamRequest: UpdateTeamRequest): Response<TeamResponse>

}
