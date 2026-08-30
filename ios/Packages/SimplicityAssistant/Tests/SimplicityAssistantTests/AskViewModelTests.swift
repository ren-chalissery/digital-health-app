import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAssistant

@Suite("AskViewModel", .serialized)
@MainActor
final class AskViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func user(orgId: UUID? = Constants.orgId) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: orgId,
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    nonisolated private static func answered() -> AnswerResponse {
        AnswerResponse(
            answer: "Pacing means matching the session to the person in front of you.",
            answered: true,
            citations: [
                CitationResponse(
                    assignedToYou: true,
                    moduleId: UUID(),
                    moduleTitle: "Trauma informed practice",
                    sectionTitle: "Asking well"
                )
            ]
        )
    }

    nonisolated private static func refused() -> AnswerResponse {
        // A refusal still carries citations in this fixture, so the view model has to be the thing
        // that suppresses them.
        AnswerResponse(
            answered: false,
            citations: [CitationResponse(assignedToYou: false, moduleTitle: "Something")]
        )
    }

    private func makeSUT(
        answer: AnswerResponse? = nil,
        fails: Bool = false,
        orgId: UUID? = Constants.orgId
    ) -> (AskViewModel, MockAssistantService) {
        let assistant = MockAssistantService(policy: .relaxed)
        if fails {
            given(assistant).ask(orgId: .any, question: .any).willThrow(TestError.unreachable)
        } else {
            given(assistant).ask(orgId: .any, question: .any)
                .willReturn(answer ?? Self.answered())
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user(orgId: orgId))

        Container.shared.assistantService.register { assistant }
        Container.shared.sessionService.register { session }
        return (AskViewModel(), assistant)
    }

    // MARK: Asking

    @Test("an empty question is not asked")
    func refusesEmptyQuestion() async {
        let (model, assistant) = makeSUT()

        await model.ask()

        #expect(model.canAsk == false)
        verify(assistant).ask(orgId: .any, question: .any).called(0)
    }

    @Test("whitespace alone is not a question")
    func refusesWhitespaceQuestion() async {
        let (model, assistant) = makeSUT()
        model.question = "   \n "

        await model.ask()

        verify(assistant).ask(orgId: .any, question: .any).called(0)
    }

    @Test("without an active organisation nothing is asked")
    func refusesWithoutOrganisation() async {
        let (model, assistant) = makeSUT(orgId: nil)
        model.question = "What is pacing?"

        await model.ask()

        verify(assistant).ask(orgId: .any, question: .any).called(0)
    }

    @Test("an answer is exposed with its citations")
    func exposesAnswer() async {
        let (model, _) = makeSUT()
        model.question = "What is pacing?"

        await model.ask()

        #expect(model.answer?.answered == true)
        #expect(model.citations.count == 1)
        #expect(model.errorMessage == nil)
    }

    // MARK: Refusal

    @Test("a refusal is not an error — it is the assistant working correctly")
    func refusalIsNotAnError() async {
        // The whole design of Phase 4 rests on this. A question the training does not cover has no
        // answer, and saying so is the safe behaviour, not a failure to be retried.
        let (model, _) = makeSUT(answer: Self.refused())
        model.question = "Should I prescribe an SSRI?"

        await model.ask()

        #expect(model.answer?.answered == false)
        #expect(model.errorMessage == nil)
    }

    @Test("a refusal shows no citations, even when the payload carries some")
    func refusalShowsNoCitations() async {
        let (model, _) = makeSUT(answer: Self.refused())
        model.question = "Should I prescribe an SSRI?"

        await model.ask()

        #expect(model.citations.isEmpty)
    }

    // MARK: Failure

    @Test("a failed request is an error, and reads differently from a refusal")
    func failureIsAnError() async {
        let (model, _) = makeSUT(fails: true)
        model.question = "What is pacing?"

        await model.ask()

        #expect(model.answer == nil)
        #expect(model.errorMessage != nil)
        #expect(model.isAsking == false)
    }

    // MARK: Single turn

    @Test("asking again replaces the previous answer rather than accumulating history")
    func replacesPreviousAnswer() async {
        let (model, _) = makeSUT()
        model.question = "What is pacing?"
        await model.ask()

        model.question = "And what about consent?"
        await model.ask()

        #expect(model.answer?.answered == true)
    }

    @Test("reset clears the question and the answer")
    func resetClears() async {
        let (model, _) = makeSUT()
        model.question = "What is pacing?"
        await model.ask()

        model.reset()

        #expect(model.question.isEmpty)
        #expect(model.answer == nil)
    }
}
