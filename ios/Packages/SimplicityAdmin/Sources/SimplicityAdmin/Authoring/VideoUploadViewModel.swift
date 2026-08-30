import Factory
import Foundation
import SimplicityApi
import SimplicityServices

public enum UploadState: Equatable, Sendable {
    case idle
    case tooLarge
    case uploading
    case processing
    case ready
    case failed
}

@Observable
@MainActor
public final class VideoUploadViewModel {

    // MARK: Types

    private enum Constants {
        /// The server's own cap, from AppProperties.maxUploadBytes.
        static let maximumBytes: Int64 = 524_288_000
        static let maximumPolls = 240
    }

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.mediaService) private var media
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var state: UploadState = .idle
    public private(set) var progress: Double = 0
    public private(set) var asset: MediaAssetResponse?
    public private(set) var errorMessage: String?

    public var assetId: UUID? { asset?.assetId }

    private let uploader: VideoUploader
    private let pollInterval: Duration
    private var orgId: UUID?

    // MARK: Init

    /// `pollInterval` is injected so tests do not sleep for real. A suite that waits five seconds
    /// to prove polling stops is a suite people stop running.
    public init(
        uploader: VideoUploader = BackgroundVideoUploader(),
        pollInterval: Duration = .seconds(5)
    ) {
        self.uploader = uploader
        self.pollInterval = pollInterval
    }

    // MARK: Functions

    public func start(fileURL: URL, filename: String, sizeBytes: Int64, contentType: String) async {
        // Before anything is registered. Failing a twenty-minute upload for a reason knowable in
        // the first second is the worst version of this, and there is no resume to soften it.
        guard sizeBytes <= Constants.maximumBytes else {
            state = .tooLarge
            return
        }

        orgId = await session.current?.activeOrganisationId
        guard let orgId else { return }

        state = .uploading
        progress = 0
        errorMessage = nil

        do {
            let target = try await media.register(
                orgId: orgId,
                filename: filename,
                contentType: contentType,
                sizeBytes: sizeBytes
            )
            guard
                let assetId = target.assetId,
                let uploadUrl = target.uploadUrl.flatMap(URL.init(string:))
            else {
                state = .failed
                errorMessage = String(localized: "upload_failed", bundle: .module)
                return
            }

            try await uploader.upload(
                fileURL: fileURL,
                to: uploadUrl,
                contentType: contentType,
                progress: { [weak self] fraction in
                    Task { @MainActor in self?.progress = fraction }
                }
            )

            // Only after the bytes have arrived. Marking uploaded first would start a transcode
            // of a file that is not there.
            try await media.markUploaded(orgId: orgId, assetId: assetId)
            state = .processing
            await pollUntilFinished(assetId: assetId)
        } catch {
            state = .failed
            errorMessage = String(localized: "upload_failed", bundle: .module)
        }
    }

    /// Stops on ready **and** on failed. Polling past a failed transcode drains a battery and
    /// tells the author nothing.
    public func pollUntilFinished(assetId: UUID) async {
        guard let orgId else { return }

        for _ in 0..<Constants.maximumPolls {
            do {
                let current = try await media.asset(orgId: orgId, assetId: assetId)
                asset = current

                if current?.isReady == true {
                    state = .ready
                    return
                }
                if current?.mediaStatus == .failed {
                    state = .failed
                    errorMessage = current?.failureReason
                        ?? String(localized: "upload_transcode_failed", bundle: .module)
                    return
                }
            } catch {
                // A failed poll is not a failed transcode. Keep waiting rather than declaring the
                // video broken because one request did not land.
                errorMessage = nil
            }

            try? await Task.sleep(for: pollInterval)
        }

        errorMessage = String(localized: "upload_still_processing", bundle: .module)
    }
}
