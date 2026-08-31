package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.CurrentUserResponse
import io.simplicity.training.api.models.SetActiveOrganisationRequest
import io.simplicity.training.api.models.UpdateProfileRequest

interface CurrentUserApi {
    /**
     * GET api/v1/me
     * Describe the signed-in user
     * Provisions the user on first call. Clients use profileCompleted and the organisations list to decide whether to show onboarding.
     * Responses:
     *  - 200: OK
     *
     * @return [CurrentUserResponse]
     */
    @GET("api/v1/me")
    suspend fun getCurrentUser(): Response<CurrentUserResponse>

    /**
     * PUT api/v1/me/active-organisation
     * Choose which organisation to work in
     * Stored on the user so the choice follows them to another device. Refused for any organisation that is not a live membership of the caller&#39;s.
     * Responses:
     *  - 200: OK
     *
     * @param setActiveOrganisationRequest 
     * @return [CurrentUserResponse]
     */
    @PUT("api/v1/me/active-organisation")
    suspend fun setActiveOrganisation(@Body setActiveOrganisationRequest: SetActiveOrganisationRequest): Response<CurrentUserResponse>

    /**
     * PUT api/v1/me/profile
     * Complete or update the professional profile
     * Sets profileCompleted, which is what lets the client leave the wizard.
     * Responses:
     *  - 200: OK
     *
     * @param updateProfileRequest 
     * @return [CurrentUserResponse]
     */
    @PUT("api/v1/me/profile")
    suspend fun updateProfile(@Body updateProfileRequest: UpdateProfileRequest): Response<CurrentUserResponse>

}
