package io.simplicity.training.admin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.MediaAssetResponse
import io.simplicity.training.api.models.UploadTargetResponse
import io.simplicity.training.services.MediaService
import io.simplicity.training.services.MediaStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VideoUploadViewModelTest {

    private val media = mockk<MediaService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()
    private val assetId: UUID = UUID.randomUUID()
    private var uploaded = false

    private fun sut() = VideoUploadViewModel(media, orgId) { _, _ -> uploaded = true }

    @Test
    fun `an accepted video is registered, uploaded and completed`() = runTest {
        coEvery { media.register(any(), any(), any(), any()) } returns
            UploadTargetResponse(assetId = assetId, uploadUrl = "https://s3.test/put")
        coEvery { media.completeUpload(orgId, assetId) } returns
            MediaAssetResponse(assetId = assetId, status = MediaStatus.PROCESSING)

        sut().upload("clip.mp4", "video/mp4", 1024)

        assertTrue(uploaded)
        coVerify(exactly = 1) { media.completeUpload(orgId, assetId) }
    }

    /**
     * Checked before anything is sent. The server enforces it too, but failing a half-gigabyte
     * upload at the end for a reason knowable at the start would waste minutes of somebody's
     * evening on hospital wifi.
     */
    @Test
    fun `a video over the cap is refused before anything is uploaded`() = runTest {
        val model = sut()

        model.upload("huge.mp4", "video/mp4", VideoUploadViewModel.MAX_UPLOAD_BYTES + 1)

        assertTrue(model.state.value.tooLarge)
        assertFalse(uploaded)
        coVerify(exactly = 0) { media.register(any(), any(), any(), any()) }
    }

    @Test
    fun `a video exactly at the cap is allowed`() = runTest {
        coEvery { media.register(any(), any(), any(), any()) } returns
            UploadTargetResponse(assetId = assetId, uploadUrl = "https://s3.test/put")

        val model = sut()
        model.upload("big.mp4", "video/mp4", VideoUploadViewModel.MAX_UPLOAD_BYTES)

        assertFalse("the cap is inclusive", model.state.value.tooLarge)
    }

    @Test
    fun `a file that is not video is refused`() = runTest {
        val model = sut()

        model.upload("notes.pdf", "application/pdf", 1024)

        assertTrue(model.state.value.wrongType)
        coVerify(exactly = 0) { media.register(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed upload is reported rather than left looking successful`() = runTest {
        coEvery { media.register(any(), any(), any(), any()) } throws IllegalStateException("offline")

        val model = sut()
        model.upload("clip.mp4", "video/mp4", 1024)

        assertTrue(model.state.value.failed)
    }

    @Test
    fun `polling stops as soon as transcoding is ready`() = runTest {
        coEvery { media.asset(orgId, assetId) } returns
            MediaAssetResponse(assetId = assetId, status = MediaStatus.READY)

        val model = sut()
        model.awaitTranscoding(assetId, attempts = 10, intervalMillis = 0)

        assertTrue(model.state.value.isReady)
        coVerify(exactly = 1) { media.asset(orgId, assetId) }
    }

    /** A failed transcode is a terminal state too, and polling for it forever helps nobody. */
    @Test
    fun `polling stops when transcoding fails`() = runTest {
        coEvery { media.asset(orgId, assetId) } returns
            MediaAssetResponse(assetId = assetId, status = MediaStatus.FAILED, failureReason = "bad audio")

        val model = sut()
        model.awaitTranscoding(assetId, attempts = 10, intervalMillis = 0)

        assertTrue(model.state.value.didFailTranscoding)
        coVerify(exactly = 1) { media.asset(orgId, assetId) }
    }
}
