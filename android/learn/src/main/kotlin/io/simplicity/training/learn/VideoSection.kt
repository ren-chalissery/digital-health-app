package io.simplicity.training.learn

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import io.simplicity.training.api.models.PlaybackResponse

/**
 * Builds what the player is handed for a training video.
 *
 * **Captions work here and do not on iOS.** `AVFoundation` refuses to side-load a WebVTT track
 * onto a progressive MP4 — it wants HLS, which would mean a transcode change, a data migration and
 * republishing every module. Media3 attaches the track directly, so Android and the web offer
 * captions and iOS does not. That divergence is deliberate and recorded in the README, because it
 * is the answer somebody in support will need.
 */
object VideoSection {

    /**
     * @throws InsecurePlaybackUrl if the URL is not HTTPS. Playback URLs are presigned by the API
     *   and are always HTTPS; anything else is a bug at best, and playing it anyway would send a
     *   clinician's session over a channel that can be read.
     */
    fun mediaItem(playback: PlaybackResponse): MediaItem {
        val url = playback.url.orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw InsecurePlaybackUrl(url)
        }

        val captions = playback.captionUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?.let { caption ->
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(caption))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        // On by default. In a shared clinical space that is the accessible choice
                        // and costs nothing to turn off.
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                )
            }
            .orEmpty()

        return MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(captions)
            .build()
    }
}

class InsecurePlaybackUrl(url: String) :
    IllegalArgumentException("Refusing to play a video over something other than HTTPS: $url")
