import Foundation

/// The subset of Markdown that module sections are written in — the same grammar the web's
/// renderer accepts, expressed as values rather than as HTML.
public enum MarkdownBlock: Equatable {
    case heading(level: Int, text: AttributedString)
    case paragraph(AttributedString)
    case bullets([AttributedString])
    case numbered([AttributedString])
    case code(String)
}
