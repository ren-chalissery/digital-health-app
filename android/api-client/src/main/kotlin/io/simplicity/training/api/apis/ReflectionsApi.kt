package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.ReflectionResponse
import io.simplicity.training.api.models.WriteReflectionRequest

interface ReflectionsApi {
    /**
     * DELETE api/v1/me/reflections/{reflectionId}
     * Delete one
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param reflectionId 
     * @return [Unit]
     */
    @DELETE("api/v1/me/reflections/{reflectionId}")
    suspend fun deleteReflection(@Path("reflectionId") reflectionId: java.util.UUID): Response<Unit>

    /**
     * PUT api/v1/me/reflections/{reflectionId}
     * Edit one
     * 
     * Responses:
     *  - 200: OK
     *
     * @param reflectionId 
     * @param writeReflectionRequest 
     * @return [ReflectionResponse]
     */
    @PUT("api/v1/me/reflections/{reflectionId}")
    suspend fun editReflection(@Path("reflectionId") reflectionId: java.util.UUID, @Body writeReflectionRequest: WriteReflectionRequest): Response<ReflectionResponse>

    /**
     * GET api/v1/me/reflections/{reflectionId}
     * Read one
     * Somebody else&#39;s returns 404, because a 403 would confirm that it exists.
     * Responses:
     *  - 200: OK
     *
     * @param reflectionId 
     * @return [ReflectionResponse]
     */
    @GET("api/v1/me/reflections/{reflectionId}")
    suspend fun getReflection(@Path("reflectionId") reflectionId: java.util.UUID): Response<ReflectionResponse>

    /**
     * GET api/v1/me/reflections
     * The caller&#39;s reflections, newest first, or those matching a search
     * 
     * Responses:
     *  - 200: OK
     *
     * @param q  (optional)
     * @return [kotlin.collections.List<ReflectionResponse>]
     */
    @GET("api/v1/me/reflections")
    suspend fun listReflections(@Query("q") q: kotlin.String? = null): Response<kotlin.collections.List<ReflectionResponse>>

    /**
     * POST api/v1/me/reflections
     * Write a reflection
     * 
     * Responses:
     *  - 201: Created
     *
     * @param writeReflectionRequest 
     * @return [ReflectionResponse]
     */
    @POST("api/v1/me/reflections")
    suspend fun writeReflection(@Body writeReflectionRequest: WriteReflectionRequest): Response<ReflectionResponse>

}
