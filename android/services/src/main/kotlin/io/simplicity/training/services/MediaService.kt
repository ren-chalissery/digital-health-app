package io.simplicity.training.services

import io.simplicity.training.api.apis.MediaApi
import io.simplicity.training.api.models.MediaAssetResponse
import io.simplicity.training.api.models.RegisterUploadRequest
import io.simplicity.training.api.models.UploadTargetResponse
import java.util.UUID

/**
 * Video upload and its transcoding.
 *
 * There is no endpoint for a single asset, only a list, so [asset] filters. That is worth stating
 * rather than hiding: polling a list to watch one item transcode is the shape the API forces, and
 * a future `GET /media/{id}` should replace it.
 */
interface MediaService {
    suspend fun register(orgId: UUID, filename: String, contentType: String, sizeBytes: Long): UploadTargetResponse
    suspend fun completeUpload(orgId: UUID, assetId: UUID): MediaAssetResponse
    suspend fun list(orgId: UUID): List<MediaAssetResponse>
    suspend fun asset(orgId: UUID, assetId: UUID): MediaAssetResponse?
}

class MediaServiceImpl(private val api: MediaApi) : MediaService {

    override suspend fun register(orgId: UUID, filename: String, contentType: String, sizeBytes: Long) =
        api.registerUpload(
            orgId,
            RegisterUploadRequest(contentType = contentType, filename = filename, sizeBytes = sizeBytes),
        ).unwrap()

    override suspend fun completeUpload(orgId: UUID, assetId: UUID) =
        api.completeUpload(orgId, assetId).unwrap()

    override suspend fun list(orgId: UUID) = api.listMedia(orgId).unwrap()

    override suspend fun asset(orgId: UUID, assetId: UUID) =
        list(orgId).firstOrNull { it.assetId == assetId }
}

/** The states the server reports. A string on the wire, so the client spells them out once. */
object MediaStatus {
    const val UPLOADING = "UPLOADING"
    const val PROCESSING = "PROCESSING"
    const val READY = "READY"
    const val FAILED = "FAILED"
}
