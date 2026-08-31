package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.AssignedModuleResponse
import io.simplicity.training.api.models.AttemptResultResponse
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.PlaybackResponse
import io.simplicity.training.api.models.QuizResponse
import io.simplicity.training.api.models.SubmitAttemptRequest

interface LearningApi {
    /**
     * PUT api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete
     * Mark a section as read
     * Completing the last section completes the module in the same transaction.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param sectionId 
     * @return [LearnerModuleResponse]
     */
    @PUT("api/v1/orgs/{orgId}/learning/sections/{sectionId}/complete")
    suspend fun completeSection(@Path("orgId") orgId: java.util.UUID, @Path("sectionId") sectionId: java.util.UUID): Response<LearnerModuleResponse>

    /**
     * GET api/v1/orgs/{orgId}/learning/media/{assetId}/playback
     * A short-lived URL for a video in an assigned module
     * Minted per request after the same assignment check that guards the module. Holding an asset id is not authorisation.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param assetId 
     * @return [PlaybackResponse]
     */
    @GET("api/v1/orgs/{orgId}/learning/media/{assetId}/playback")
    suspend fun getPlaybackUrl(@Path("orgId") orgId: java.util.UUID, @Path("assetId") assetId: java.util.UUID): Response<PlaybackResponse>

    /**
     * GET api/v1/orgs/{orgId}/learning/{moduleId}/quiz
     * The quiz for an assigned module
     * Questions and options only. Which option is correct is never sent here.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @return [QuizResponse]
     */
    @GET("api/v1/orgs/{orgId}/learning/{moduleId}/quiz")
    suspend fun getQuiz(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID): Response<QuizResponse>

    /**
     * GET api/v1/orgs/{orgId}/learning
     * Modules assigned to the caller&#39;s teams, with their progress
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<AssignedModuleResponse>]
     */
    @GET("api/v1/orgs/{orgId}/learning")
    suspend fun listAssignedModules(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<AssignedModuleResponse>>

    /**
     * GET api/v1/orgs/{orgId}/learning/{moduleId}
     * The published version of one assigned module
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @return [LearnerModuleResponse]
     */
    @GET("api/v1/orgs/{orgId}/learning/{moduleId}")
    suspend fun readModule(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID): Response<LearnerModuleResponse>

    /**
     * POST api/v1/orgs/{orgId}/learning/{moduleId}/quiz/attempts
     * Answer the quiz
     * Marked on the server. Returns which questions were right, the correct answer, and the author&#39;s explanation. Passing completes the module if every section is read.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param moduleId 
     * @param submitAttemptRequest 
     * @return [AttemptResultResponse]
     */
    @POST("api/v1/orgs/{orgId}/learning/{moduleId}/quiz/attempts")
    suspend fun submitQuizAttempt(@Path("orgId") orgId: java.util.UUID, @Path("moduleId") moduleId: java.util.UUID, @Body submitAttemptRequest: SubmitAttemptRequest): Response<AttemptResultResponse>

}
