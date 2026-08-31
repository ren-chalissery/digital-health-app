package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.AssignTeamsRequest
import io.simplicity.training.api.models.AuthoredModuleResponse
import io.simplicity.training.api.models.CreateModuleRequest
import io.simplicity.training.api.models.ModuleSummaryResponse
import io.simplicity.training.api.models.PublishRequest
import io.simplicity.training.api.models.ReplaceQuizRequest
import io.simplicity.training.api.models.ReplaceSectionsRequest
import io.simplicity.training.api.models.UpdateModuleRequest

interface ModulesApi {
    /**
     * DELETE api/v1/orgs/{orgId}/modules/{moduleId}
     * Archive a module
     * Hidden from learners and authors alike. Completions and history are kept.
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param moduleId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/modules/{moduleId}")
    suspend fun archiveModule(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID): Response<Unit>

    /**
     * PUT api/v1/orgs/{orgId}/modules/{moduleId}/teams
     * Set which teams this module is assigned to
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param assignTeamsRequest 
     * @return [AuthoredModuleResponse]
     */
    @PUT("api/v1/orgs/{orgId}/modules/{moduleId}/teams")
    suspend fun assignModuleToTeams(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body assignTeamsRequest: AssignTeamsRequest): Response<AuthoredModuleResponse>

    /**
     * POST api/v1/orgs/{orgId}/modules
     * Create a module
     * Comes with an empty first draft, since a module with no version cannot be edited.
     * Responses:
     *  - 201: Created
     *
     * @param orgId 
     * @param createModuleRequest 
     * @return [AuthoredModuleResponse]
     */
    @POST("api/v1/orgs/{orgId}/modules")
    suspend fun createModule(@Path("orgId") orgId: java.util.UUID, @Body createModuleRequest: CreateModuleRequest): Response<AuthoredModuleResponse>

    /**
     * GET api/v1/orgs/{orgId}/modules/{moduleId}
     * One module, with both the published version and the draft
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @return [AuthoredModuleResponse]
     */
    @GET("api/v1/orgs/{orgId}/modules/{moduleId}")
    suspend fun getModule(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID): Response<AuthoredModuleResponse>

    /**
     * GET api/v1/orgs/{orgId}/modules
     * List this organisation&#39;s modules
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<ModuleSummaryResponse>]
     */
    @GET("api/v1/orgs/{orgId}/modules")
    suspend fun listModules(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<ModuleSummaryResponse>>

    /**
     * POST api/v1/orgs/{orgId}/modules/{moduleId}/draft
     * Open a draft
     * Copies what learners currently have, so an edit starts from the live content.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @return [AuthoredModuleResponse]
     */
    @POST("api/v1/orgs/{orgId}/modules/{moduleId}/draft")
    suspend fun openModuleDraft(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID): Response<AuthoredModuleResponse>

    /**
     * POST api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish
     * Publish the draft
     * Set supersedesCompletions when the change is substantive, which sends anyone who completed an earlier version back through it. A corrected typo should not.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param publishRequest 
     * @return [AuthoredModuleResponse]
     */
    @POST("api/v1/orgs/{orgId}/modules/{moduleId}/draft/publish")
    suspend fun publishModule(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body publishRequest: PublishRequest): Response<AuthoredModuleResponse>

    /**
     * PUT api/v1/orgs/{orgId}/modules/{moduleId}/draft/quiz
     * Replace the draft&#39;s quiz questions
     * Each question needs at least two options and exactly one correct one; publishing refuses anything else, since a question with no answer can never be passed.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param replaceQuizRequest 
     * @return [AuthoredModuleResponse]
     */
    @PUT("api/v1/orgs/{orgId}/modules/{moduleId}/draft/quiz")
    suspend fun replaceModuleQuiz(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body replaceQuizRequest: ReplaceQuizRequest): Response<AuthoredModuleResponse>

    /**
     * PUT api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections
     * Replace the draft&#39;s sections
     * Sent whole: editing, reordering, and deleting all happen on one screen.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param replaceSectionsRequest 
     * @return [AuthoredModuleResponse]
     */
    @PUT("api/v1/orgs/{orgId}/modules/{moduleId}/draft/sections")
    suspend fun replaceModuleSections(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body replaceSectionsRequest: ReplaceSectionsRequest): Response<AuthoredModuleResponse>

    /**
     * PATCH api/v1/orgs/{orgId}/modules/{moduleId}
     * Rename a module or change its summary
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param updateModuleRequest 
     * @return [AuthoredModuleResponse]
     */
    @PATCH("api/v1/orgs/{orgId}/modules/{moduleId}")
    suspend fun updateModule(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body updateModuleRequest: UpdateModuleRequest): Response<AuthoredModuleResponse>

}
