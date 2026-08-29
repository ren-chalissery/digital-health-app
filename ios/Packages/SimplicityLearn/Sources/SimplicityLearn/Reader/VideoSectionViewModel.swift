import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class VideoSectionViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.learningService) private var learning
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let assetId: UUID

    public private(set) var isLoading = false
    public private(set) var errorMessage: String?
    public private(set) var playbackURL: URL?

    /// The video has captions that this app cannot render.
    ///
    /// AVFoundation only exposes subtitle renditions declared inside the asset, and the transcode
    /// emits a progressive MP4 with no manifest to declare one in. Rather than drop an
    /// accessibility feature silently, the player says so and points at the web, where the
    /// `<track>` element works.
    public private(set) var hasUnavailableCaptions = false

    // MARK: Init

    public init(assetId: UUID) {
        self.assetId = assetId
    }

    // MARK: Functions

    /// Playback URLs expire, so they are fetched when the video is asked for rather than for
    /// every section of a module up front.
    public func load() async {
        guard
            playbackURL == nil,
            !isLoading,
            let orgId = await session.current?.activeOrganisationId
        else {
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let playback = try await learning.playback(orgId: orgId, assetId: assetId)
            guard let url = Self.playableURL(playback.url) else {
                // A player handed a nil URL renders an empty black box, which reads as a broken
                // video rather than a failed request.
                errorMessage = String(localized: "video_load_failed", bundle: .module)
                return
            }
            playbackURL = url
            hasUnavailableCaptions = playback.captionUrl?.isEmpty == false
        } catch {
            errorMessage = String(localized: "video_load_failed", bundle: .module)
        }
    }

    // MARK: Private

    /// `URL(string:)` alone is not a check. It percent-encodes almost anything into a valid
    /// relative URL, so "not a url at all" parses happily and then fails silently in the player.
    ///
    /// Requiring an absolute `https` URL with a host is both the parse check and the right rule:
    /// playback URLs are presigned CloudFront links, and training content should never be fetched
    /// in plaintext.
    private static func playableURL(_ raw: String?) -> URL? {
        guard
            let raw,
            let url = URL(string: raw),
            url.scheme?.lowercased() == "https",
            url.host?.isEmpty == false
        else {
            return nil
        }
        return url
    }
}
