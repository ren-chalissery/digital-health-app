import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityLearn

@Suite("VideoSectionViewModel", .serialized)
@MainActor
final class VideoSectionViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let assetId = UUID()
        static let url = "https://media.example.com/video.mp4?signature=abc"
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId,
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        playback: PlaybackResponse? = PlaybackResponse(expiresInSeconds: 900, url: Constants.url),
        fails: Bool = false
    ) -> (VideoSectionViewModel, MockLearningService) {
        let learning = MockLearningService(policy: .relaxed)
        if fails {
            given(learning).playback(orgId: .any, assetId: .any).willThrow(TestError.unreachable)
        } else if let playback {
            given(learning).playback(orgId: .any, assetId: .any).willReturn(playback)
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.learningService.register { learning }
        Container.shared.sessionService.register { session }
        return (VideoSectionViewModel(assetId: Constants.assetId), learning)
    }

    @Test("exposes the URL the server signed")
    func exposesURL() async {
        let (model, _) = makeSUT()

        await model.load()

        #expect(model.playbackURL?.absoluteString == Constants.url)
        #expect(model.errorMessage == nil)
    }

    @Test("nonsense is a failure, not an empty black player")
    func unparseableURL() async {
        // URL(string:) alone would accept this by percent-encoding it into a relative URL.
        let (model, _) = makeSUT(playback: PlaybackResponse(url: "not a url at all"))

        await model.load()

        #expect(model.playbackURL == nil)
        #expect(model.errorMessage != nil)
    }

    @Test("refuses plaintext, so training content is never fetched over http")
    func refusesPlaintext() async {
        let (model, _) = makeSUT(playback: PlaybackResponse(url: "http://media.example.com/v.mp4"))

        await model.load()

        #expect(model.playbackURL == nil)
        #expect(model.errorMessage != nil)
    }

    @Test("refuses a URL with no host")
    func refusesHostlessURL() async {
        let (model, _) = makeSUT(playback: PlaybackResponse(url: "https:///video.mp4"))

        await model.load()

        #expect(model.playbackURL == nil)
    }

    @Test("a caption track we cannot render is acknowledged rather than dropped silently")
    func acknowledgesUnavailableCaptions() async {
        let (model, _) = makeSUT(
            playback: PlaybackResponse(
                captionUrl: "https://media.example.com/captions.vtt",
                url: Constants.url
            )
        )

        await model.load()

        #expect(model.hasUnavailableCaptions)
    }

    @Test("does not claim captions exist when the video has none")
    func noCaptionsClaimedWhenAbsent() async {
        let (model, _) = makeSUT()

        await model.load()

        #expect(model.hasUnavailableCaptions == false)
    }

    @Test("a failure says so and leaves no player behind")
    func failedLoad() async {
        let (model, _) = makeSUT(fails: true)

        await model.load()

        #expect(model.playbackURL == nil)
        #expect(model.errorMessage != nil)
        #expect(model.isLoading == false)
    }

    @Test("does not refetch a URL it already holds, which would burn a signed request per redraw")
    func doesNotRefetch() async {
        let (model, learning) = makeSUT()

        await model.load()
        await model.load()

        verify(learning).playback(orgId: .any, assetId: .any).called(1)
    }
}
