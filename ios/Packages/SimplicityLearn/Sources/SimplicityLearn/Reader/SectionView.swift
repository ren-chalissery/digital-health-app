import SimplicityApi
import SimplicityDesign
import SwiftUI

struct SectionView: View {

    // MARK: Properties

    let section: SectionResponse
    let isRead: Bool
    let isSaving: Bool
    let onMarkRead: () -> Void

    // MARK: SwiftUI

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x3) {
            header

            #if os(iOS)
            if let assetId = section.mediaAssetId {
                VideoSectionView(assetId: assetId)
            }
            #endif

            MarkdownText(section.body ?? "")

            if !isRead {
                PrimaryButton(
                    title: isSaving
                        ? String(localized: "reader_saving", bundle: .module)
                        : String(localized: "reader_mark_read", bundle: .module),
                    isLoading: isSaving,
                    action: onMarkRead
                )
            }
        }
        .padding(.vertical, Spacing.x3)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.x3) {
            Text(verbatim: section.title ?? "")
                .font(.brandTitle)
                .accessibilityAddTraits(.isHeader)

            Spacer()

            if isRead {
                Text("reader_read", bundle: .module)
                    .font(.brandCaption)
                    .padding(.horizontal, Spacing.x2)
                    .padding(.vertical, Spacing.x1)
                    .background(Color.brandPrimary.opacity(0.12))
                    .clipShape(Capsule())
            }
        }
    }
}
