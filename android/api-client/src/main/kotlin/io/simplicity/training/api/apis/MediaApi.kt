package io.simplicity.training.api.apis

import io.simplicity.training.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.simplicity.training.api.models.MediaAssetResponse
import io.simplicity.training.api.models.RegisterUploadRequest
import io.simplicity.training.api.models.UploadTargetResponse

interface MediaApi {
    /**
     * POST api/v1/orgs/{orgId}/media/{assetId}/uploaded
     * Report that the upload finished
     * Hands the file to the transcoder; the asset becomes PROCESSING.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param assetId 
     * @return [MediaAssetResponse]
     */
    @POST("api/v1/orgs/{orgId}/media/{assetId}/uploaded")
    suspend fun completeUpload(@Path("orgId") orgId: java.util.UUID, @Path("assetId") assetId: java.util.UUID): Response<MediaAssetResponse>

    /**
     * DELETE api/v1/orgs/{orgId}/media/{assetId}
     * Delete a video
     * Any section using it keeps its writing and loses the video.
     * Responses:
     *  - 204: No Content
     *
     * @param orgId 
     * @param assetId 
     * @return [Unit]
     */
    @DELETE("api/v1/orgs/{orgId}/media/{assetId}")
    suspend fun deleteMedia(@Path("orgId") orgId: java.util.UUID, @Path("assetId") assetId: java.util.UUID): Response<Unit>

    /**
     * GET api/v1/orgs/{orgId}/media
     * The organisation&#39;s video library
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @return [kotlin.collections.List<MediaAssetResponse>]
     */
    @GET("api/v1/orgs/{orgId}/media")
    suspend fun listMedia(@Path("orgId") orgId: java.util.UUID): Response<kotlin.collections.List<MediaAssetResponse>>

    /**
     * POST api/v1/orgs/{orgId}/media
     * Register a video and get somewhere to put it
     * Returns a presigned URL the browser PUTs to directly. Video never passes through the API on the way in.
     * Responses:
     *  - 201: Created
     *
     * @param orgId 
     * @param registerUploadRequest 
     * @return [UploadTargetResponse]
     */
    @POST("api/v1/orgs/{orgId}/media")
    suspend fun registerUpload(@Path("orgId") orgId: java.util.UUID, @Body registerUploadRequest: RegisterUploadRequest): Response<UploadTargetResponse>

    /**
     * DELETE api/v1/orgs/{orgId}/media/{assetId}/captions
     * Remove the caption track
     * 
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param assetId 
     * @return [MediaAssetResponse]
     */
    @DELETE("api/v1/orgs/{orgId}/media/{assetId}/captions")
    suspend fun removeCaptions(@Path("orgId") orgId: java.util.UUID, @Path("assetId") assetId: java.util.UUID): Response<MediaAssetResponse>

    /**
     * PUT api/v1/orgs/{orgId}/media/{assetId}/captions
     * Attach a WebVTT caption track
     * Sent as the request body rather than presigned, because a caption file is kilobytes where a video is hundreds of megabytes.
     * Responses:
     *  - 200: OK
     *
     * @param orgId 
     * @param assetId 
     * @param body 
     * @return [MediaAssetResponse]
     */
    @PUT("api/v1/orgs/{orgId}/media/{assetId}/captions")
    suspend fun setCaptions(@Path("orgId") orgId: java.util.UUID, @Path("assetId") assetId: java.util.UUID, @Body body: kotlin.String): Response<MediaAssetResponse>

}
