import Foundation
import Testing

@testable import SimplicityDesign

@Suite("MarkdownParser")
struct MarkdownParserTests {

    private func plain(_ block: MarkdownBlock?) -> String? {
        switch block {
        case let .paragraph(text): String(text.characters)
        case let .heading(_, text): String(text.characters)
        default: nil
        }
    }

    private func paragraph(_ source: String) -> AttributedString? {
        guard case let .paragraph(text) = MarkdownParser.parse(source).first else { return nil }
        return text
    }

    // MARK: Blocks

    @Test("joins wrapped lines into one paragraph, as the web does")
    func joinsParagraphLines() {
        let blocks = MarkdownParser.parse("one\ntwo\n\nthree")
        #expect(blocks.count == 2)
        #expect(plain(blocks.first) == "one two")
        #expect(plain(blocks.last) == "three")
    }

    @Test("a hash heading starts at level two, because level one belongs to the page")
    func headingLevelsStartAtTwo() {
        #expect(MarkdownParser.parse("# Title") == [.heading(level: 2, text: AttributedString("Title"))])
        #expect(MarkdownParser.parse("### Title") == [.heading(level: 4, text: AttributedString("Title"))])
    }

    @Test("a hash with no space after it is prose, not a heading")
    func hashWithoutSpaceIsProse() {
        #expect(plain(MarkdownParser.parse("#hashtag").first) == "#hashtag")
    }

    @Test("groups consecutive bullets into one list")
    func groupsBullets() {
        guard case let .bullets(items) = MarkdownParser.parse("- one\n- two").first else {
            Issue.record("expected bullets")
            return
        }
        #expect(items.map { String($0.characters) } == ["one", "two"])
    }

    @Test("keeps numbered and bulleted lists apart rather than merging them")
    func doesNotMergeListKinds() {
        let blocks = MarkdownParser.parse("- one\n1. two")
        #expect(blocks.count == 2)
        guard case .bullets = blocks.first, case .numbered = blocks.last else {
            Issue.record("expected a bullet list then a numbered list, got \(blocks)")
            return
        }
    }

    @Test("keeps a fenced code block verbatim, including its indentation")
    func preservesCode() {
        #expect(MarkdownParser.parse("```\n  indented\n```") == [.code("  indented")])
    }

    @Test("an unterminated fence still emits its content rather than swallowing it")
    func unterminatedFence() {
        #expect(MarkdownParser.parse("```\nleft open") == [.code("left open")])
    }

    @Test("empty input produces nothing rather than an empty paragraph")
    func emptyInput() {
        #expect(MarkdownParser.parse("").isEmpty)
        #expect(MarkdownParser.parse("   \n  \n").isEmpty)
    }

    // MARK: Inline

    @Test("applies bold as an attribute rather than leaving the markers visible")
    func appliesInlineEmphasis() {
        guard let text = paragraph("a **bold** word") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(String(text.characters) == "a bold word")
        #expect(text.runs.contains { $0.inlinePresentationIntent == .stronglyEmphasized })
    }

    @Test("keeps an http link")
    func keepsHttpLink() {
        guard let text = paragraph("[site](https://example.com)") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(text.runs.contains { $0.link?.scheme == "https" })
    }

    @Test("keeps a mailto link, which the web also allows")
    func keepsMailtoLink() {
        guard let text = paragraph("[mail](mailto:someone@example.com)") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(text.runs.contains { $0.link?.scheme == "mailto" })
    }

    @Test("strips a javascript link but keeps its words, so nothing becomes tappable")
    func stripsJavascriptLink() {
        guard let text = paragraph("[tap me](javascript:alert(1))") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(String(text.characters).contains("tap me"))
        #expect(text.runs.allSatisfy { $0.link == nil })
    }

    @Test("strips a link to a scheme that could leave the app for something unexpected")
    func stripsCustomScheme() {
        guard let text = paragraph("[x](tel:0800123)") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(text.runs.allSatisfy { $0.link == nil })
    }

    @Test("does not render an image, because the web's grammar has none")
    func ignoresImages() {
        guard let text = paragraph("![alt](https://example.com/x.png)") else {
            Issue.record("expected a paragraph")
            return
        }
        #expect(text.runs.allSatisfy { $0.imageURL == nil })
    }
}
