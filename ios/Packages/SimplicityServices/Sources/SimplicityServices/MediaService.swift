import Foundation
import Mockable
import SimplicityApi

/// The backend's transcode states. Generated as a plain `String`, so this is the only place that
/// knows the spellings.
public enum MediaStatus: String, Sendable {
    case pending = "PENDING"
    case processing = "PROCESSING"
    case ready = "READY"
    case failed = "FAILED"
}

public extension MediaAssetResponse {

    /// Nil for a state this app does not know. Better an unrecognised asset than one silently
    /// treated as ready because the comparison fell through.
    var mediaStatus: MediaStatus? {
        status.flatMap(MediaStatus.init(rawValue:))
    }

    var isReady: Bool {
        mediaStatus == .ready
    }

    /// Ready and failed both end polling. Only these two are terminal.
    var isFinished: Bool {
        mediaStatus == .ready || mediaStatus == .failed
    }
}

@Mockable
public protocol MediaService: AnyObject, Sendable {
    func list(orgId: UUID) async throws -> [MediaAssetResponse]

    /// Returns the asset id and a **single** presigned PUT URL for the whole file. There are no
    /// parts, so a failed upload starts again from nothing.
    func register(
        orgId: UUID,
        filename: String,
        contentType: String,
        sizeBytes: Int64
    ) async throws -> UploadTargetResponse

    func markUploaded(orgId: UUID, assetId: UUID) async throws

    /// There is no single-asset endpoint, so this lists and filters. Wasteful for polling a
    /// transcode, but an organisation has tens of assets rather than thousands, and inventing a
    /// client-side cache to avoid it would be worse than the request.
    func asset(orgId: UUID, assetId: UUID) async throws -> MediaAssetResponse?

    /// Sent as a `text/vtt` body rather than presigned: a caption file is kilobytes where a video
    /// is hundreds of megabytes.
    func setCaptions(orgId: UUID, assetId: UUID, vtt: String) async throws
    func removeCaptions(orgId: UUID, assetId: UUID) async throws
    func delete(orgId: UUID, assetId: UUID) async throws
}
