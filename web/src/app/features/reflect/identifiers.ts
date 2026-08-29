/**
 * Spots text that looks like it identifies a patient.
 *
 * <p>This warns; it never blocks. A filter that refuses to save teaches evasion — refused a name
 * and an NHI number, somebody writes "J.S., DOB 12/3" instead, which is still identifying, is no
 * longer detectable, and now carries the false assurance that the field was checked. A warning
 * that says what it saw and why the product asks is more likely to change what gets written.
 *
 * <p>It is not a security control and must not be relied on as one. It runs in the browser, and the
 * server neither inspects reflections nor records anything about them, because scanning a private
 * journal would undercut the promise that only its author reads it.
 */

export interface IdentifierWarning {
  readonly kind: string;
  readonly explanation: string;
}

interface Rule {
  readonly kind: string;
  readonly explanation: string;
  readonly pattern: RegExp;
}

const RULES: Rule[] = [
  {
    kind: 'an NHI number',
    explanation: 'New Zealand health identifiers are three letters followed by four digits.',
    // Word boundaries both sides, so ordinary words and abbreviations are not caught.
    pattern: /\b[A-HJ-NP-Z]{3}\d{4}\b/,
  },
  {
    kind: 'a Medicare number',
    explanation: 'Australian Medicare numbers are ten digits, sometimes written in groups.',
    pattern: /\b\d{4}[ -]?\d{5}[ -]?\d\b/,
  },
  {
    kind: 'a date of birth',
    explanation: 'A full date is usually only written down when it identifies somebody.',
    pattern: /\b(0?[1-9]|[12]\d|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](19|20)\d{2}\b/,
  },
  {
    kind: 'an email address',
    explanation: 'An address names a person.',
    pattern: /\b[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}\b/,
  },
  {
    kind: 'a phone number',
    explanation: 'Contact details identify somebody directly.',
    // Long runs of digits with optional separators. A time such as 14:30 has too few.
    pattern: /(?:\+\d{1,3}[ -]?)?(?:\(?\d{2,4}\)?[ -]?){2,4}\d{3,4}\b/,
  },
];

export function findIdentifiers(text: string): IdentifierWarning[] {
  const source = text ?? '';
  return RULES.filter((rule) => rule.pattern.test(source)).map(({ kind, explanation }) => ({
    kind,
    explanation,
  }));
}
