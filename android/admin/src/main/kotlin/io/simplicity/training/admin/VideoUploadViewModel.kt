package io.simplicity.training.admin

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.MediaAssetResponse
import io.simplicity.training.services.MediaService
import io.simplicity.training.services.MediaStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class VideoUploadState(
    val asset: MediaAssetResponse? = null,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
    val tooLarge: Boolean = false,
    val wrongType: Boolean = false,
) {
    val isReady: Boolean get() = asset?.status == MediaStatus.READY
    val didFailTranscoding: Boolean get() = asset?.status == MediaStatus.FAILED
}

/**
 * Registering, uploading and watching a video transcode.
 *
 * The size and type are checked before anything is sent. Both are enforced server-side, but failing
 * a several-hundred-megabyte upload at the end for a reason knowable at the start is unkind — and
 * on hospital wifi it may be several minutes of somebody's evening.
 */
class VideoUploadViewModel(
    private val media: MediaService,
    private val orgId: UUID,
    private val putFile: suspend (url: String, contentType: String) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoUploadState())
    val state: StateFlow<VideoUploadState> = _state.asStateFlow()

    suspend fun upload(filename: String, contentType: String, sizeBytes: Long) {
        if (contentType !in ACCEPTED_TYPES) {
            _state.update { it.copy(wrongType = true) }
            return
        }
        if (sizeBytes > MAX_UPLOAD_BYTES) {
            _state.update { it.copy(tooLarge = true) }
            return
        }

        _state.update { it.copy(isBusy = true, failed = false, tooLarge = false, wrongType = false) }
        try {
            val target = media.register(orgId, filename, contentType, sizeBytes)
            putFile(target.uploadUrl.orEmpty(), contentType)
            val asset = media.completeUpload(orgId, target.assetId!!)
            _state.update { it.copy(asset = asset, isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    /**
     * Polls a list and filters, because there is no endpoint for a single asset. That is the shape
     * the API forces rather than a choice.
     */
    suspend fun awaitTranscoding(assetId: UUID, attempts: Int = 60, intervalMillis: Long = 5_000) {
        repeat(attempts) {
            val asset = media.asset(orgId, assetId)
            _state.update { state -> state.copy(asset = asset ?: state.asset) }
            if (asset?.status == MediaStatus.READY || asset?.status == MediaStatus.FAILED) return
            delay(intervalMillis)
        }
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 500L * 1024 * 1024
        val ACCEPTED_TYPES = setOf("video/mp4", "video/quicktime", "video/webm")
    }
}
