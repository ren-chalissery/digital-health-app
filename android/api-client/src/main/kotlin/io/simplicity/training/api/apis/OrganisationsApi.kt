package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.ChangeOrgRoleRequest
import io.simplicity.training.api.models.CreateOrganisationRequest
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.api.models.OrganisationResponse

interface OrganisationsApi {
    /**
     * DELETE api/v1/orgs/{orgId}
     * Archive an organisation
     * Makes it unreachable for every member while keeping its memberships, teams, and audit history. Nothing is deleted.
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}")
    suspend fun archiveOrganisation(@Path("orgId") orgId: java.util.UUID): Response<Unit>

    /**
     * PATCH api/v1/orgs/{orgId}/members/{userId}
     * Change a member&#39;s organisation role
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param userId 
     * @param changeOrgRoleRequest 
     * @return [OrgMemberResponse]
     */
    @PATCH("api/v1/orgs/{orgId}/members/{userId}")
    suspend fun changeOrganisationRole(@Path("orgId") orgId: java.util.UUID, @Path("userId") userId: java.util.UUID, @Body changeOrgRoleRequest: ChangeOrgRoleRequest): Response<OrgMemberResponse>

    /**
     * POST api/v1/organisations
     * Create an organisation
     * The caller becomes its first administrator. Used by the self-signup flow.
     * Responses:
     *  - 201: Created
     *
     * @param createOrganisationRequest 
     * @return [OrganisationResponse]
     */
    @POST("api/v1/organisations")
    suspend fun createOrganisation(@Body createOrganisationRequest: CreateOrganisationRequest): Response<OrganisationResponse>

    /**
     * GET api/v1/orgs/{orgId}
     * Fetch one organisation
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [OrganisationResponse]
     */
    @GET("api/v1/orgs/{orgId}")
    suspend fun getOrganisation(@Path("orgId") orgId: java.util.UUID): Response<OrganisationResponse>

    /**
     * DELETE api/v1/orgs/{orgId}/members/me
     * Leave an organisation
     * Ends the caller&#39;s own membership and their teams within it. The last administrator may leave, which archives the organisation rather than leaving nobody able to administer it.
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/members/me")
    suspend fun leaveOrganisation(@Path("orgId") orgId: java.util.UUID): Response<Unit>

    /**
     * GET api/v1/orgs/{orgId}/members
     * List everybody in an organisation
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<OrgMemberResponse>]
     */
    @GET("api/v1/orgs/{orgId}/members")
    suspend fun listOrganisationMembers(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<OrgMemberResponse>>

    /**
     * DELETE api/v1/orgs/{orgId}/members/{userId}
     * Remove a member, ending their team memberships in this organisation
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param userId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/members/{userId}")
    suspend fun removeOrganisationMember(@Path("orgId") orgId: java.util.UUID, @Path("userId") userId: java.util.UUID): Response<Unit>

}
