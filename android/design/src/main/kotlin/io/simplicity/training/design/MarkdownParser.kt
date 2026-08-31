package io.simplicity.training.design

/**
 * Parses the Markdown module sections are written in.
 *
 * A deliberately small grammar rather than a full library: the web renderer accepts this much and
 * no more, and matching it exactly is what stops a module looking different on a phone.
 *
 * Links are the part with a consequence. `[tap me](javascript:…)` must not become something
 * tappable, so only http, https and mailto survive as links — anything else renders as its label.
 * Images are dropped rather than rendered, as on iOS and the web.
 */
object MarkdownParser {

    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

    // One level of balanced parentheses in the destination, which CommonMark allows and a naive
    // [^)]* does not. `[tap me](javascript:alert(1))` is the case that matters: stopping at the
    // first bracket leaves a stray ")" in the rendered text and, worse, mis-reads the scheme.
    private const val DESTINATION = """((?:[^()]|\([^()]*\))*)"""
    private val IMAGE = Regex("""!\[[^]]*]\(""" + DESTINATION + """\)""")
    private val LINK = Regex("""\[([^]]*)]\(""" + DESTINATION + """\)""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET = Regex("""^[-*]\s+(.*)$""")
    private val NUMBERED = Regex("""^\d+[.)]\s+(.*)$""")

    fun parse(source: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        val numbered = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inFence = false

        fun closeParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
                paragraph.clear()
            }
        }

        fun closeBullets() {
            if (bullets.isNotEmpty()) {
                blocks += MarkdownBlock.Bullets(bullets.toList())
                bullets.clear()
            }
        }

        fun closeNumbered() {
            if (numbered.isNotEmpty()) {
                blocks += MarkdownBlock.Numbered(numbered.toList())
                numbered.clear()
            }
        }

        fun closeAll() {
            closeParagraph()
            closeBullets()
            closeNumbered()
        }

        for (raw in source.lines()) {
            val line = raw.trim()

            if (line.startsWith("```")) {
                if (inFence) {
                    blocks += MarkdownBlock.Code(code.joinToString("\n"))
                    code.clear()
                } else {
                    closeAll()
                }
                inFence = !inFence
                continue
            }
            if (inFence) {
                // Verbatim: a hyphen inside a fence is not a bullet.
                code += raw
                continue
            }

            val withoutImages = IMAGE.replace(line, "").trim()
            if (withoutImages.isEmpty()) {
                closeAll()
                continue
            }

            HEADING.find(withoutImages)?.let { match ->
                closeAll()
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = inline(match.groupValues[2]),
                )
                return@let
            } ?: run {
                BULLET.find(withoutImages)?.let { match ->
                    closeParagraph()
                    closeNumbered()
                    bullets += inline(match.groupValues[1])
                } ?: NUMBERED.find(withoutImages)?.let { match ->
                    closeParagraph()
                    closeBullets()
                    numbered += inline(match.groupValues[1])
                } ?: run {
                    closeBullets()
                    closeNumbered()
                    paragraph += inline(withoutImages)
                }
            }
        }

        if (inFence && code.isNotEmpty()) {
            blocks += MarkdownBlock.Code(code.joinToString("\n"))
        }
        closeAll()
        return blocks
    }

    /**
     * Keeps a link only when its scheme is one a reader can be handed safely; otherwise the label
     * survives and the target is discarded.
     */
    private fun inline(text: String): String = LINK.replace(text) { match ->
        val label = match.groupValues[1]
        val target = match.groupValues[2]
        val scheme = target.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme in ALLOWED_SCHEMES) match.value else label
    }
}
