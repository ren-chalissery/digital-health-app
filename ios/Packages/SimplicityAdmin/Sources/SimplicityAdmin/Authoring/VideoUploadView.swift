#if os(iOS)
import PhotosUI
import SimplicityDesign
import SwiftUI

/// Picking or filming a video and sending it to S3.
///
/// iOS only: PhotosUI has no macOS equivalent here, and the package stays buildable on macOS so
/// its view models can be tested with `swift test`.
public struct VideoUploadView: View {

    // MARK: Properties

    @State private var model = VideoUploadViewModel()
    @State private var picked: PhotosPickerItem?
    private let onAttached: (UUID) -> Void

    // MARK: Init

    public init(onAttached: @escaping (UUID) -> Void) {
        self.onAttached = onAttached
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x3) {
            PhotosPicker(selection: $picked, matching: .videos) {
                Label {
                    Text("upload_choose", bundle: .module)
                } icon: {
                    Image(systemName: "film")
                }
            }
            .disabled(model.state == .uploading)
            .accessibilityIdentifier("pick-video")

            statusView

            ErrorBanner(message: model.errorMessage)
        }
        .onChange(of: picked) { _, item in
            guard let item else { return }
            Task { await handle(item) }
        }
        .onChange(of: model.state) { _, state in
            if state == .ready, let assetId = model.assetId {
                onAttached(assetId)
            }
        }
    }

    // MARK: Private

    @ViewBuilder
    private var statusView: some View {
        switch model.state {
        case .idle:
            EmptyView()

        case .tooLarge:
            Text("upload_too_large", bundle: .module)
                .font(.brandCaption)
                .foregroundStyle(Color.brandDanger)

        case .uploading:
            VStack(alignment: .leading, spacing: Spacing.x1) {
                Text("upload_uploading", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
                ProgressView(value: model.progress)
            }

        case .processing:
            // Named explicitly: a video is not usable the moment the bytes arrive, and silence at
            // this point reads as a hang.
            Text("upload_processing", bundle: .module)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)

        case .ready:
            Label {
                Text("upload_ready", bundle: .module)
            } icon: {
                Image(systemName: "checkmark.circle")
            }
            .font(.brandCaption)
            .foregroundStyle(Color.brandPrimary)

        case .failed:
            EmptyView()
        }
    }

    /// A background upload needs a file URL that outlives the picker, so the movie is copied to a
    /// temporary file first — never into Documents, which would be backed up.
    private func handle(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }

        let filename = "\(UUID().uuidString).mp4"
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        guard (try? data.write(to: fileURL)) != nil else { return }
        defer { try? FileManager.default.removeItem(at: fileURL) }

        await model.start(
            fileURL: fileURL,
            filename: filename,
            sizeBytes: Int64(data.count),
            contentType: "video/mp4"
        )
    }
}
#endif
