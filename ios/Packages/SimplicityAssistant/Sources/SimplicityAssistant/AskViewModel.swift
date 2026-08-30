import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class AskViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.assistantService) private var assistant
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public var question: String = .empty
    public private(set) var isAsking = false
    public private(set) var errorMessage: String?
    public private(set) var answer: AnswerResponse?

    public var canAsk: Bool {
        !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isAsking
    }

    /// Only shown for an answered question. A refusal carries no citations, and showing any would
    /// suggest the training nearly covered it.
    public var citations: [CitationResponse] {
        guard answer?.answered == true else { return [] }
        return answer?.citations ?? []
    }

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func ask() async {
        guard canAsk, let orgId = await session.current?.activeOrganisationId else { return }

        isAsking = true
        errorMessage = nil
        // Single turn: a new question replaces the previous answer rather than adding to it.
        answer = nil
        defer { isAsking = false }

        do {
            answer = try await assistant.ask(orgId: orgId, question: question)
        } catch {
            // Distinct from an unanswered question. This one means we could not ask.
            errorMessage = String(localized: "ask_failed", bundle: .module)
        }
    }

    public func reset() {
        question = .empty
        answer = nil
        errorMessage = nil
    }
}
