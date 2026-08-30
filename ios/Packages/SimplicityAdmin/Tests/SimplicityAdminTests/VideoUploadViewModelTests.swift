import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("VideoUploadViewModel", .serialized)
@MainActor
final class VideoUploadViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let assetId = UUID()
        static let uploadUrl = "https://s3.example.com/put?signature=abc"
        static let underCap: Int64 = 1_000_000
        static let overCap: Int64 = 524_288_001
    }

    private enum TestError: Error {
        case unreachable
    }

    private struct SUT {
        let model: VideoUploadViewModel
        let recorder: Recorder
        let media: MockMediaService
    }

    /// Records what happened and in what order, because the order is the assertion.
    private final class Recorder: @unchecked Sendable {
        private let lock = NSLock()
        private var steps: [String] = []

        var value: [String] { lock.withLock { steps } }
        func record(_ step: String) { lock.withLock { steps.append(step) } }
    }

    private struct StubUploader: VideoUploader {
        let recorder: Recorder
        let shouldFail: Bool

        func upload(
            fileURL: URL,
            to target: URL,
            contentType: String,
            progress: @escaping @Sendable (Double) -> Void
        ) async throws {
            recorder.record("upload")
            progress(0.5)
            if shouldFail { throw TestError.unreachable }
            progress(1)
        }
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId, id: UUID(), profileCompleted: true,
            status: .active
        )
    }

    nonisolated private static func asset(
        status: String,
        failureReason: String? = nil
    ) -> MediaAssetResponse {
        MediaAssetResponse(
            assetId: Constants.assetId,
            failureReason: failureReason,
            filename: "clip.mp4",
            status: status
        )
    }

    private func makeSUT(
        uploadFails: Bool = false,
        pollStates: [MediaAssetResponse]? = nil
    ) -> SUT {
        // Resolved here rather than as a default argument: `Self` is covariant on a subclass and
        // cannot be referenced from one.
        let states = pollStates ?? [Self.asset(status: "READY")]
        let recorder = Recorder()
        let media = MockMediaService(policy: .relaxed)

        given(media).register(orgId: .any, filename: .any, contentType: .any, sizeBytes: .any)
            .willProduce { _, _, _, _ in
                recorder.record("register")
                return UploadTargetResponse(
                    assetId: Constants.assetId, uploadUrl: Constants.uploadUrl
                )
            }
        given(media).markUploaded(orgId: .any, assetId: .any).willProduce { _, _ in
            recorder.record("markUploaded")
        }
        var remaining = states
        given(media).asset(orgId: .any, assetId: .any).willProduce { _, _ in
            remaining.isEmpty ? states.last : remaining.removeFirst()
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.mediaService.register { media }
        Container.shared.sessionService.register { session }

        let model = VideoUploadViewModel(
            uploader: StubUploader(recorder: recorder, shouldFail: uploadFails),
            pollInterval: .zero
        )
        return SUT(model: model, recorder: recorder, media: media)
    }

    private func start(_ model: VideoUploadViewModel, size: Int64) async {
        await model.start(
            fileURL: URL(fileURLWithPath: "/tmp/clip.mp4"),
            filename: "clip.mp4",
            sizeBytes: size,
            contentType: "video/mp4"
        )
    }

    // MARK: Size

    @Test("a file over the cap is refused without registering anything")
    func tooLargeRegistersNothing() async {
        // There is no resume, so failing a long upload for a reason knowable at the start is the
        // worst version of this.
        let sut = makeSUT()

        await start(sut.model, size: Constants.overCap)

        #expect(sut.model.state == .tooLarge)
        verify(sut.media)
            .register(orgId: .any, filename: .any, contentType: .any, sizeBytes: .any)
            .called(0)
    }

    // MARK: Order

    @Test("registers, uploads, then marks uploaded — in that order")
    func ordersTheThreeSteps() async {
        let sut = makeSUT()

        await start(sut.model, size: Constants.underCap)

        #expect(sut.recorder.value == ["register", "upload", "markUploaded"])
    }

    @Test("a failed upload does not mark uploaded, so no transcode starts for absent bytes")
    func failedUploadDoesNotComplete() async {
        let sut = makeSUT(uploadFails: true)

        await start(sut.model, size: Constants.underCap)

        #expect(sut.recorder.value.contains("markUploaded") == false)
        verify(sut.media).markUploaded(orgId: .any, assetId: .any).called(0)
        #expect(sut.model.state == .failed)
        #expect(sut.model.errorMessage != nil)
    }

    @Test("progress is reported and reaches one")
    func reportsProgress() async {
        let sut = makeSUT()

        await start(sut.model, size: Constants.underCap)

        #expect(sut.model.progress == 1)
    }

    // MARK: Polling

    @Test("polling stops when the transcode is ready")
    func stopsOnReady() async {
        let sut = makeSUT(pollStates: [
            Self.asset(status: "PROCESSING"),
            Self.asset(status: "READY")
        ])

        await start(sut.model, size: Constants.underCap)

        #expect(sut.model.state == .ready)
    }

    @Test("polling stops when the transcode fails, rather than waiting forever")
    func stopsOnFailed() async {
        let sut = makeSUT(pollStates: [Self.asset(status: "FAILED")])

        await start(sut.model, size: Constants.underCap)

        #expect(sut.model.state == .failed)
    }

    @Test("a failed transcode surfaces the server's reason when it gives one")
    func surfacesFailureReason() async {
        let sut = makeSUT(pollStates: [
            Self.asset(status: "FAILED", failureReason: "No audio track")
        ])

        await start(sut.model, size: Constants.underCap)

        #expect(sut.model.errorMessage == "No audio track")
    }
}
