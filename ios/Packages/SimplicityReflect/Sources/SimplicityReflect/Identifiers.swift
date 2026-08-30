import Foundation

/// Spots text that looks like it identifies a patient.
///
/// This warns; it never blocks. A filter that refuses to save teaches evasion — refused a name and
/// an NHI number, somebody writes "J.S., DOB 12/3" instead, which is still identifying, is no
/// longer detectable, and now carries the false assurance that the field was checked. A warning
/// that says what it saw and why the product asks is more likely to change what gets written.
///
/// It is **not a security control** and must not be relied on as one. It runs on the device, and
/// the server neither inspects reflections nor records anything about them, because scanning a
/// private journal would undercut the promise that only its author reads it.
///
/// Ported from `web/src/app/features/reflect/identifiers.ts`, and meant to stay in step with it.
public struct IdentifierWarning: Equatable, Sendable {
    public let kind: String
    public let explanation: String
}

public enum Identifiers {

    private struct Rule {
        let kind: String
        let explanation: String
        let pattern: NSRegularExpression
    }

    /// `[0-9]` rather than `\d` throughout. ICU's `\d` also matches Arabic-Indic and other
    /// non-ASCII numerals, where JavaScript's does not — left as `\d`, the two clients would
    /// disagree about what counts as an identifier.
    private static let rules: [Rule] = [
        make(
            kind: "an NHI number",
            explanation: "New Zealand health identifiers are three letters followed by four digits.",
            // Word boundaries both sides, so ordinary words and abbreviations are not caught.
            pattern: #"\b[A-HJ-NP-Z]{3}[0-9]{4}\b"#
        ),
        make(
            kind: "a Medicare number",
            explanation: "Australian Medicare numbers are ten digits, sometimes written in groups.",
            pattern: #"\b[0-9]{4}[ -]?[0-9]{5}[ -]?[0-9]\b"#
        ),
        make(
            kind: "a date of birth",
            explanation: "A full date is usually only written down when it identifies somebody.",
            pattern: #"\b(0?[1-9]|[12][0-9]|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](19|20)[0-9]{2}\b"#
        ),
        make(
            kind: "an email address",
            explanation: "An address names a person.",
            pattern: #"\b[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}\b"#
        ),
        make(
            kind: "a phone number",
            explanation: "Contact details identify somebody directly.",
            // Long runs of digits with optional separators. A time such as 14:30 has too few.
            pattern: #"(\+[0-9]{1,3}[ -]?)?(\(?[0-9]{2,4}\)?[ -]?){2,4}[0-9]{3,4}\b"#
        )
    ]

    /// One warning per kind, however many times that kind appears — the point is to prompt a
    /// rethink, not to enumerate every match.
    public static func find(in text: String) -> [IdentifierWarning] {
        let range = NSRange(text.startIndex..., in: text)
        return rules
            .filter { $0.pattern.firstMatch(in: text, range: range) != nil }
            .map { IdentifierWarning(kind: $0.kind, explanation: $0.explanation) }
    }

    // MARK: Private

    private static func make(kind: String, explanation: String, pattern: String) -> Rule {
        guard let expression = try? NSRegularExpression(pattern: pattern) else {
            // A pattern here is a literal in this file, so a failure is a programming error found
            // the first time the file is loaded rather than something a user can provoke.
            fatalError("Invalid identifier pattern: \(pattern)")
        }
        return Rule(kind: kind, explanation: explanation, pattern: expression)
    }
}
