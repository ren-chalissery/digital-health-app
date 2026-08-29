import Foundation

/// Parses the Markdown subset that module sections are written in.
///
/// The web renders to HTML and has to escape every character of author input first, because a
/// module author's markup would otherwise execute in a colleague's browser. Here there is no HTML
/// and no script, so that whole class of risk is absent and escaping would be ceremony.
///
/// One part of it does carry over. `AttributedString(markdown:)` will happily turn
/// `[tap me](javascript:…)` into a tappable link that SwiftUI will open, so the link-scheme
/// allowlist is the piece worth keeping — along with refusing images, which the web's grammar
/// has no syntax for either.
public enum MarkdownParser {

    /// Block structure is parsed line by line, mirroring the web renderer's grammar. Only the
    /// inline formatting within a line is handed to Foundation.
    public static func parse(_ source: String) -> [MarkdownBlock] {
        var accumulator = Accumulator()
        for line in lines(of: source) {
            accumulator.consume(line)
        }
        return accumulator.finish()
    }

    private static func lines(of source: String) -> [String] {
        source
            .replacingOccurrences(of: "\r\n", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map(String.init)
    }
}

// MARK: - Accumulation

/// One line at a time, holding whatever block is still open.
///
/// A block is only emitted once something ends it — a blank line, a different kind of line, or the
/// end of the input — which is why this is a value that outlives each line rather than a fold.
private struct Accumulator {

    private enum Constants {
        static let fence = "```"
        static let maximumHeadingLevel = 3
    }

    private var blocks: [MarkdownBlock] = []
    private var paragraph: [String] = []
    private var bullets: [AttributedString] = []
    private var numbered: [AttributedString] = []
    private var code: [String] = []
    private var inCode = false

    mutating func consume(_ line: String) {
        let trimmed = line.trimmingCharacters(in: .whitespaces)

        if trimmed.hasPrefix(Constants.fence) {
            toggleFence()
            return
        }
        if inCode {
            code.append(line)
            return
        }
        if let heading = MarkdownGrammar.heading(in: trimmed, maximumLevel: Constants.maximumHeadingLevel) {
            closeAll()
            blocks.append(heading)
            return
        }
        if let item = MarkdownGrammar.bullet(in: trimmed) {
            closeParagraph()
            closeNumbered()
            bullets.append(MarkdownGrammar.inline(item))
            return
        }
        if let item = MarkdownGrammar.numbered(in: trimmed) {
            closeParagraph()
            closeBullets()
            numbered.append(MarkdownGrammar.inline(item))
            return
        }
        if trimmed.isEmpty {
            closeAll()
            return
        }
        paragraph.append(trimmed)
    }

    mutating func finish() -> [MarkdownBlock] {
        // An unterminated fence still emits what it captured. Losing an author's content because
        // they forgot to close it would be worse than rendering it as code.
        if inCode, !code.isEmpty {
            blocks.append(.code(code.joined(separator: "\n")))
            code = []
        }
        closeAll()
        return blocks
    }

    // MARK: Private

    private mutating func toggleFence() {
        if inCode {
            blocks.append(.code(code.joined(separator: "\n")))
            code = []
        } else {
            closeAll()
        }
        inCode.toggle()
    }

    private mutating func closeParagraph() {
        guard !paragraph.isEmpty else { return }
        blocks.append(.paragraph(MarkdownGrammar.inline(paragraph.joined(separator: " "))))
        paragraph = []
    }

    private mutating func closeBullets() {
        guard !bullets.isEmpty else { return }
        blocks.append(.bullets(bullets))
        bullets = []
    }

    private mutating func closeNumbered() {
        guard !numbered.isEmpty else { return }
        blocks.append(.numbered(numbered))
        numbered = []
    }

    private mutating func closeAll() {
        closeParagraph()
        closeBullets()
        closeNumbered()
    }
}

// MARK: - Line grammar

private enum MarkdownGrammar {

    /// Anything else — tel:, javascript:, a custom scheme — renders as plain text.
    private static let allowedSchemes: Set<String> = ["http", "https", "mailto"]

    /// `#` is level two: level one belongs to the page, not to a section's content. Same rule the
    /// web renderer applies.
    static func heading(in trimmed: String, maximumLevel: Int) -> MarkdownBlock? {
        let hashes = trimmed.prefix { $0 == "#" }.count
        guard (1...maximumLevel).contains(hashes) else { return nil }

        let rest = trimmed.dropFirst(hashes)
        guard rest.first == " " else { return nil }

        return .heading(level: hashes + 1, text: inline(rest.trimmingCharacters(in: .whitespaces)))
    }

    static func bullet(in trimmed: String) -> String? {
        guard trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") else { return nil }
        return String(trimmed.dropFirst(2))
    }

    static func numbered(in trimmed: String) -> String? {
        let digits = trimmed.prefix { $0.isNumber }
        guard !digits.isEmpty else { return nil }

        let afterDigits = trimmed.dropFirst(digits.count)
        guard afterDigits.first == "." || afterDigits.first == ")" else { return nil }

        let body = afterDigits.dropFirst()
        guard body.first == " " else { return nil }
        return String(body.dropFirst())
    }

    /// Foundation handles emphasis, code spans and links; anything the web's grammar would not
    /// have produced is then removed.
    static func inline(_ text: String) -> AttributedString {
        var options = AttributedString.MarkdownParsingOptions()
        options.interpretedSyntax = .inlineOnlyPreservingWhitespace

        guard var attributed = try? AttributedString(markdown: text, options: options) else {
            return AttributedString(text)
        }

        for run in attributed.runs {
            if run.link != nil, !allowedSchemes.contains(run.link?.scheme?.lowercased() ?? "") {
                // Keep the words, drop the tap. Removing the text too would hide from the author
                // that their link was rejected.
                attributed[run.range].link = nil
            }
            if run.imageURL != nil {
                attributed[run.range].imageURL = nil
            }
        }
        return attributed
    }
}
