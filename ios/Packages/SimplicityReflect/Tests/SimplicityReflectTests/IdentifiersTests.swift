import Testing

@testable import SimplicityReflect

/// Ported case for case from `web/src/app/features/reflect/identifiers.spec.ts`. The negative
/// cases matter as much as the positive ones: a warning that fires on ordinary clinical writing
/// becomes noise, and noise is ignored.
@Suite("Identifiers")
struct IdentifiersTests {

    private func kinds(_ text: String) -> [String] {
        Identifiers.find(in: text).map(\.kind)
    }

    // MARK: Finds

    @Test("spots an NHI number")
    func findsNHI() {
        #expect(kinds("Discussed with ZZZ0016 about pacing.").contains("an NHI number"))
    }

    @Test("spots a Medicare number")
    func findsMedicare() {
        #expect(kinds("Card 2123 45670 1 was presented.").contains("a Medicare number"))
    }

    @Test("spots a date of birth")
    func findsDateOfBirth() {
        #expect(kinds("Born 12/03/1984.").contains("a date of birth"))
    }

    @Test("spots an email address")
    func findsEmail() {
        #expect(kinds("Follow up with ada@example.org.").contains("an email address"))
    }

    @Test("spots a phone number")
    func findsPhone() {
        #expect(kinds("Called 021 555 0134 afterwards.").contains("a phone number"))
    }

    @Test("reports each kind once, however many times it appears")
    func reportsEachKindOnce() {
        #expect(kinds("ABC1234 and DEF5678") == ["an NHI number"])
    }

    @Test("reports every kind it finds, not just the first")
    func findsSeveralKinds() {
        let found = kinds("Follow up with ada@example.org, born 12/03/1984.")
        #expect(found.contains("an email address"))
        #expect(found.contains("a date of birth"))
    }

    @Test("each warning explains why the product is asking")
    func warningsExplainThemselves() {
        #expect(Identifiers.find(in: "ZZZ0016").first?.explanation.isEmpty == false)
    }

    @Test("a non-ASCII digit does not slip past a rule the web would have caught")
    func nonAsciiDigits() {
        // ICU's \d matches Arabic-Indic numerals; JavaScript's does not. Anchoring to [0-9] keeps
        // the two clients agreeing about what counts as an identifier.
        #expect(kinds("Born ١٢/٠٣/١٩٨٤.").isEmpty)
    }

    // MARK: Leaves alone

    @Test("leaves an ordinary reflection alone")
    func leavesProseAlone() {
        let text = """
            I noticed I rushed the opening and did not check whether the pace suited them. \
            Next time I will slow down and ask.
            """

        #expect(Identifiers.find(in: text).isEmpty)
    }

    @Test("does not mistake a time of day for a phone number")
    func timeIsNotAPhoneNumber() {
        #expect(kinds("The session ran from 14:30 to 15:20.").isEmpty)
    }

    @Test("does not mistake a dosage for an identifier")
    func dosageIsNotAnIdentifier() {
        #expect(kinds("Titrated to 50mg over 3 weeks.").isEmpty)
    }

    @Test("does not flag a year on its own")
    func yearIsNotADateOfBirth() {
        #expect(kinds("We covered the 2019 guidance.").isEmpty)
    }

    @Test("does not flag clinical shorthand in capitals")
    func shorthandIsNotAnNHI() {
        #expect(kinds("Used CBT and ACT techniques throughout.").isEmpty)
    }

    @Test("empty text produces no warnings")
    func emptyText() {
        #expect(Identifiers.find(in: "").isEmpty)
    }
}
