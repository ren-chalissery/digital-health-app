package io.simplicity.training.learn

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import io.simplicity.training.api.models.PlaybackResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric because `MediaItem` builds a `Uri`, which is an Android type with no JVM stub.
 *
 * The captions case is the one worth having: it is the only behaviour in this app that iOS cannot
 * offer, so there is no second client to notice if it regresses.
 */
// Pinned low deliberately. Robolectric refuses SDK 37 (it emulates to 36), and its SDK 36
// sandbox needs Java 21 while this build is on 17. Nothing here is version-sensitive — it builds
// a Uri and a MediaItem — so the oldest supported level is the least fragile choice.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoSectionTest {

    private fun playback(url: String?, caption: String? = null) =
        PlaybackResponse(url = url, captionUrl = caption)

    @Test
    fun `a caption track is attached, as WebVTT, and selected by default`() {
        val item = VideoSection.mediaItem(
            playback("https://cdn.test/v.mp4", "https://cdn.test/c.vtt"),
        )

        val subtitles = item.localConfiguration!!.subtitleConfigurations
        assertEquals(1, subtitles.size)
        assertEquals(MimeTypes.TEXT_VTT, subtitles.first().mimeType)
        assertEquals(C.SELECTION_FLAG_DEFAULT, subtitles.first().selectionFlags)
    }

    @Test
    fun `a video without captions simply has none`() {
        val item = VideoSection.mediaItem(playback("https://cdn.test/v.mp4"))

        assertTrue(item.localConfiguration!!.subtitleConfigurations.isEmpty())
    }

    @Test
    fun `an http playback url is refused rather than played`() {
        assertThrows(InsecurePlaybackUrl::class.java) {
            VideoSection.mediaItem(playback("http://cdn.test/v.mp4"))
        }
    }

    @Test
    fun `a missing playback url is refused`() {
        assertThrows(InsecurePlaybackUrl::class.java) {
            VideoSection.mediaItem(playback(null))
        }
    }

    /** The video is still worth playing; the caption track is what gets dropped. */
    @Test
    fun `an insecure caption url is dropped without failing the video`() {
        val item = VideoSection.mediaItem(
            playback("https://cdn.test/v.mp4", "http://cdn.test/c.vtt"),
        )

        assertTrue(item.localConfiguration!!.subtitleConfigurations.isEmpty())
    }
}
