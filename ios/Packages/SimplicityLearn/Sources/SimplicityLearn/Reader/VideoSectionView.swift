// iOS only: AVAudioSession does not exist on macOS, and the package stays buildable there so
// its logic can be tested with `swift test`.
#if os(iOS)
import AVFoundation
import AVKit
import SimplicityDesign
import SwiftUI

public struct VideoSectionView: View {

    // MARK: Properties

    @State private var model: VideoSectionViewModel
    @State private var player: AVPlayer?

    // MARK: Init

    public init(assetId: UUID) {
        self._model = State(initialValue: VideoSectionViewModel(assetId: assetId))
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            if let player {
                // Stock transport controls, unlike tinderbox2_ionic which hides them to build
                // frame-stepping for a review tool. They bring AirPlay, Picture-in-Picture and
                // scrubbing at no cost, and a training video needs none of the rest.
                VideoPlayer(player: player)
                    .aspectRatio(16 / 9, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))

                if model.hasUnavailableCaptions {
                    Text("video_captions_elsewhere", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)
                }
            } else if model.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 120)
            } else {
                Button {
                    Task { await load() }
                } label: {
                    Label {
                        Text("video_load", bundle: .module)
                    } icon: {
                        Image(systemName: "play.circle")
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 120)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
            }

            ErrorBanner(message: model.errorMessage)
        }
    }

    // MARK: Private

    private func load() async {
        await model.load()
        guard let url = model.playbackURL else { return }

        // Without this a training video is silenced by the ring/silent switch, which reads as a
        // video with no sound rather than a phone on mute.
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)

        player = AVPlayer(url: url)
    }
}
#endif
