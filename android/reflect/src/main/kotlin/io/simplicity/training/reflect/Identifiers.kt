package io.simplicity.training.reflect

/**
 * Spots text that looks like it identifies a patient.
 *
 * This warns; it never blocks. A filter that refuses to save teaches evasion — refused a name and
 * an NHI number, somebody writes "J.S., DOB 12/3" instead, which is still identifying, is no longer
 * detectable, and now carries the false assurance that the field was checked. A warning that says
 * what it saw and why the product asks is more likely to change what gets written.
 *
 * It is **not a security control** and must not be relied on as one. It runs on the device, and the
 * server neither inspects reflections nor records anything about them, because scanning a private
 * journal would undercut the promise that only its author reads it.
 *
 * Ported from `Identifiers.swift`, which was itself ported from the web's `identifiers.ts`, and
 * meant to stay in step with both.
 */
data class IdentifierWarning(val kind: String, val explanation: String)

object Identifiers {

    private data class Rule(val kind: String, val explanation: String, val pattern: Regex)

    /**
     * `[0-9]` rather than `\d` throughout, exactly as the other two clients do.
     *
     * Java's `\d` is ASCII-only by default but ICU's is not, and JavaScript's is not either without
     * a flag. Spelling the class out means all three clients agree about what counts as a digit
     * rather than depending on each platform's idea of one.
     */
    private val rules = listOf(
        Rule(
            kind = "an NHI number",
            explanation = "New Zealand health identifiers are three letters followed by four digits.",
            // Word boundaries both sides, so ordinary words and abbreviations are not caught.
            pattern = Regex("""\b[A-HJ-NP-Z]{3}[0-9]{4}\b"""),
        ),
        Rule(
            kind = "a Medicare number",
            explanation = "Australian Medicare numbers are ten digits, sometimes written in groups.",
            pattern = Regex("""\b[0-9]{4}[ -]?[0-9]{5}[ -]?[0-9]\b"""),
        ),
        Rule(
            kind = "a date of birth",
            explanation = "A full date is usually only written down when it identifies somebody.",
            pattern = Regex("""\b(0?[1-9]|[12][0-9]|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](19|20)[0-9]{2}\b"""),
        ),
        Rule(
            kind = "an email address",
            explanation = "An address names a person.",
            pattern = Regex("""\b[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}\b"""),
        ),
        Rule(
            kind = "a phone number",
            explanation = "Contact details identify somebody directly.",
            // Long runs of digits with optional separators. A time such as 14:30 has too few.
            pattern = Regex("""(\+[0-9]{1,3}[ -]?)?(\(?[0-9]{2,4}\)?[ -]?){2,4}[0-9]{3,4}\b"""),
        ),
    )

    fun find(text: String): List<IdentifierWarning> =
        rules.filter { it.pattern.containsMatchIn(text) }
            .map { IdentifierWarning(it.kind, it.explanation) }
}
