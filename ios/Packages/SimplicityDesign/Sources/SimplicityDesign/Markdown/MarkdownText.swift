import SwiftUI

/// Renders a module section's Markdown as native views.
public struct MarkdownText: View {

    // MARK: Properties

    private let blocks: [MarkdownBlock]

    // MARK: Init

    public init(_ source: String) {
        self.blocks = MarkdownParser.parse(source)
    }

    // MARK: SwiftUI

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x3) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Private

    @ViewBuilder
    private func view(for block: MarkdownBlock) -> some View {
        switch block {
        case let .heading(level, text):
            Text(text)
                .font(level <= 2 ? .brandTitle : .brandBody.weight(.semibold))
                // Announced as a heading, so VoiceOver's rotor can jump between sections instead
                // of reading the whole module to find one.
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)

        case let .paragraph(text):
            Text(text)
                .font(.brandBody)
                .frame(maxWidth: .infinity, alignment: .leading)

        case let .bullets(items):
            list(items) { _ in "•" }

        case let .numbered(items):
            list(items) { "\($0 + 1)." }

        case let .code(source):
            Text(verbatim: source)
                .font(.system(.callout, design: .monospaced))
                .padding(Spacing.x3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
        }
    }

    private func list(
        _ items: [AttributedString],
        marker: @escaping (Int) -> String
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .firstTextBaseline, spacing: Spacing.x2) {
                    Text(verbatim: marker(index))
                        .font(.brandBody)
                        .foregroundStyle(Color.brandTextSecondary)
                    Text(item)
                        .font(.brandBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
