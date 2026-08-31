package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.AnswerResponse
import io.simplicity.training.api.models.AskRequest

interface AssistantApi {
    /**
     * POST api/v1/orgs/{orgId}/assistant/questions
     * Ask a question about the training
     * Answers only from published modules in this organisation, with citations. When the material does not cover the question it says so rather than guessing, and no model is called. It never reads reflections and never gives clinical advice.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param askRequest 
     * @return [AnswerResponse]
     */
    @POST("api/v1/orgs/{orgId}/assistant/questions")
    suspend fun askAssistant(@Path("orgId") orgId: java.util.UUID, @Body askRequest: AskRequest): Response<AnswerResponse>

}
