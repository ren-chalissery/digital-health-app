package io.simplicity.training.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The same grammar `MarkdownParserTests.swift` asserts.
 *
 * The link cases come first because they are the only ones with a security consequence. A module
 * is written by an administrator, so this is not defence against a stranger — but an
 * administrator's account can be compromised, and the phone should not be a weaker reader of the
 * same content than the web.
 */
class MarkdownParserTest {

    @Test
    fun `a javascript link is rendered as text, not as something tappable`() {
        val blocks = MarkdownParser.parse("[tap me](javascript:alert(1))")

        assertEquals(listOf(MarkdownBlock.Paragraph("tap me")), blocks)
    }

    /**
     * The case that caught a real bug. A naive `[^)]*` destination stops at the first bracket,
     * leaves a stray ")" in the text, and reads the scheme wrongly — and a payload with brackets
     * in it is exactly what somebody exploiting this would write.
     */
    @Test
    fun `a javascript link with brackets in the payload is still defused cleanly`() {
        val blocks = MarkdownParser.parse("[go](javascript:alert(document.domain))")

        assertEquals(listOf(MarkdownBlock.Paragraph("go")), blocks)
    }

    @Test
    fun `a tel link is rendered as text`() {
        val blocks = MarkdownParser.parse("[call](tel:+64211234567)")

        assertEquals(listOf(MarkdownBlock.Paragraph("call")), blocks)
    }

    @Test
    fun `http, https and mailto survive`() {
        for (scheme in listOf("http://a.test", "https://a.test", "mailto:a@b.test")) {
            val blocks = MarkdownParser.parse("[link]($scheme)")
            assertEquals(
                "expected $scheme to be kept",
                listOf(MarkdownBlock.Paragraph("[link]($scheme)")),
                blocks,
            )
        }
    }

    /** Images are ignored outright rather than rendered, as on iOS and the web. */
    @Test
    fun `an image is dropped`() {
        val blocks = MarkdownParser.parse("![alt](https://a.test/x.png)")

        assertEquals(emptyList<MarkdownBlock>(), blocks)
    }

    @Test
    fun `headings carry their level`() {
        assertEquals(
            listOf(MarkdownBlock.Heading(2, "Safety")),
            MarkdownParser.parse("## Safety"),
        )
    }

    @Test
    fun `consecutive lines are one paragraph and a blank line ends it`() {
        val blocks = MarkdownParser.parse("one\ntwo\n\nthree")

        assertEquals(
            listOf(MarkdownBlock.Paragraph("one two"), MarkdownBlock.Paragraph("three")),
            blocks,
        )
    }

    @Test
    fun `bullets gather into one block`() {
        val blocks = MarkdownParser.parse("- first\n- second")

        assertEquals(listOf(MarkdownBlock.Bullets(listOf("first", "second"))), blocks)
    }

    @Test
    fun `numbered items gather into one block`() {
        val blocks = MarkdownParser.parse("1. first\n2. second")

        assertEquals(listOf(MarkdownBlock.Numbered(listOf("first", "second"))), blocks)
    }

    @Test
    fun `a fenced block keeps its text verbatim`() {
        val blocks = MarkdownParser.parse("```\n- not a bullet\n```")

        assertEquals(listOf(MarkdownBlock.Code("- not a bullet")), blocks)
    }

    @Test
    fun `empty input produces nothing`() {
        assertEquals(emptyList<MarkdownBlock>(), MarkdownParser.parse(""))
    }
}
