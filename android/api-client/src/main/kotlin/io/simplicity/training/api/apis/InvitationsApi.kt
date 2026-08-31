package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.CreateInvitationRequest
import io.simplicity.training.api.models.InvitationPreviewResponse
import io.simplicity.training.api.models.InvitationResponse

interface InvitationsApi {
    /**
     * POST api/v1/invitations/{token}/accept
     * Accept an invitation as the signed-in user
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param token 
     * @return [Unit]
     */
    @POST("api/v1/invitations/{token}/accept")
    suspend fun acceptInvitation(@Path("token") token: kotlin.String): Response<Unit>

    /**
     * POST api/v1/orgs/{orgId}/invitations
     * Invite somebody to the organisation
     * Re-inviting an address withdraws the outstanding invitation and issues a fresh link, so only one token is ever live for a given address.
     * Responses:
     *  - 201: Created
     *
     * @param orgId 
     * @param createInvitationRequest 
     * @return [InvitationResponse]
     */
    @POST("api/v1/orgs/{orgId}/invitations")
    suspend fun createInvitation(@Path("orgId") orgId: java.util.UUID, @Body createInvitationRequest: CreateInvitationRequest): Response<InvitationResponse>

    /**
     * GET api/v1/orgs/{orgId}/invitations
     * List an organisation&#39;s invitations
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<InvitationResponse>]
     */
    @GET("api/v1/orgs/{orgId}/invitations")
    suspend fun listInvitations(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<InvitationResponse>>

    /**
     * GET api/v1/invitations/{token}
     * Preview an invitation before signing up
     * Public. Returns valid&#x3D;false for anything unknown, expired, or already used, so the endpoint cannot be used to probe for live tokens.
     * Responses:
     *  - 200: OK
     *
     * @param token 
     * @return [InvitationPreviewResponse]
     */
    @GET("api/v1/invitations/{token}")
    suspend fun previewInvitation(@Path("token") token: kotlin.String): Response<InvitationPreviewResponse>

    /**
     * DELETE api/v1/orgs/{orgId}/invitations/{invitationId}
     * Withdraw an outstanding invitation
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param invitationId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/invitations/{invitationId}")
    suspend fun revokeInvitation(@Path("orgId") orgId: java.util.UUID, @Path("invitationId") invitationId: java.util.UUID): Response<Unit>

}
