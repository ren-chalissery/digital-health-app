package io.simplicity.training.design

/**
 * The subset of Markdown module sections are written in — the same grammar the web's renderer
 * accepts, expressed as values rather than as HTML.
 *
 * Mirrors `MarkdownBlock.swift`. Values rather than rendered output so the parser can be tested
 * without a screen.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullets(val items: List<String>) : MarkdownBlock
    data class Numbered(val items: List<String>) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}
