import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityLearn

@Suite("QuizViewModel", .serialized)
@MainActor
final class QuizViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
        static let questionOne = UUID()
        static let questionTwo = UUID()
        static let optionA = UUID()
        static let optionB = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func quiz() -> QuizResponse {
        QuizResponse(
            attemptCount: 0,
            passed: false,
            questions: [
                QuizQuestionResponse(
                    options: [
                        QuizOptionResponse(label: "A", optionId: Constants.optionA),
                        QuizOptionResponse(label: "B", optionId: Constants.optionB)
                    ],
                    prompt: "First?",
                    questionId: Constants.questionOne
                ),
                QuizQuestionResponse(
                    options: [
                        QuizOptionResponse(label: "A", optionId: Constants.optionA),
                        QuizOptionResponse(label: "B", optionId: Constants.optionB)
                    ],
                    prompt: "Second?",
                    questionId: Constants.questionTwo
                )
            ]
        )
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId,
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    nonisolated private static func passedResult() -> AttemptResultResponse {
        AttemptResultResponse(
            attemptNumber: 1,
            correctCount: 2,
            passed: true,
            questionCount: 2,
            questions: [
                MarkedQuestion(questionId: Constants.questionOne, wasCorrect: true),
                MarkedQuestion(
                    explanation: "Because.",
                    questionId: Constants.questionTwo,
                    wasCorrect: false
                )
            ]
        )
    }

    private func makeSUT(
        result: AttemptResultResponse? = nil,
        submitFails: Bool = false
    ) -> (QuizViewModel, MockLearningService) {
        let learning = MockLearningService(policy: .relaxed)
        if submitFails {
            given(learning).submitAttempt(orgId: .any, moduleId: .any, answers: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(learning).submitAttempt(orgId: .any, moduleId: .any, answers: .any)
                .willReturn(result ?? Self.passedResult())
        }
        given(learning).module(orgId: .any, moduleId: .any).willReturn(
            LearnerModuleResponse(moduleId: Constants.moduleId, status: .completed)
        )

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.learningService.register { learning }
        Container.shared.sessionService.register { session }
        return (QuizViewModel(moduleId: Constants.moduleId, quiz: Self.quiz()), learning)
    }

    // MARK: Answering

    @Test("not all answered until every question has a choice")
    func allAnsweredRequiresEveryQuestion() {
        let (model, _) = makeSUT()
        #expect(model.allAnswered == false)

        model.choose(question: Constants.questionOne, option: Constants.optionA)
        #expect(model.allAnswered == false)

        model.choose(question: Constants.questionTwo, option: Constants.optionB)
        #expect(model.allAnswered)
    }

    @Test("choosing again replaces the answer rather than adding a second")
    func choosingReplaces() {
        let (model, _) = makeSUT()

        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionOne, option: Constants.optionB)

        #expect(model.chosen.count == 1)
        #expect(model.chosen[Constants.questionOne] == Constants.optionB)
    }

    // MARK: Submitting

    @Test("refuses to submit a partly answered quiz, without a request")
    func refusesPartialSubmission() async {
        let (model, learning) = makeSUT()
        model.choose(question: Constants.questionOne, option: Constants.optionA)

        await model.submit()

        #expect(model.result == nil)
        verify(learning).submitAttempt(orgId: .any, moduleId: .any, answers: .any).called(0)
    }

    @Test("sends one answer per question")
    func sendsOneAnswerPerQuestion() async {
        let (model, learning) = makeSUT()
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)

        await model.submit()

        verify(learning).submitAttempt(orgId: .any, moduleId: .any, answers: .any).called(1)
        #expect(model.result?.passed == true)
    }

    @Test("passing tells the reader, because only it knows the module may now be complete")
    func passingNotifiesTheReader() async {
        let (model, _) = makeSUT()
        let received = Received()
        model.onModuleChanged = { module in
            Task { await received.set(module) }
        }
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)

        await model.submit()
        await Task.yield()

        #expect(await received.value?.status == .completed)
    }

    @Test("a second press clears the marking instead of resubmitting the same answers")
    func secondPressIsTryAgain() async {
        let (model, learning) = makeSUT()
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)
        await model.submit()

        await model.submit()

        #expect(model.result == nil)
        #expect(model.chosen.isEmpty)
        verify(learning).submitAttempt(orgId: .any, moduleId: .any, answers: .any).called(1)
    }

    @Test("a failed submission says so and records no result")
    func failedSubmission() async {
        let (model, _) = makeSUT(submitFails: true)
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)

        await model.submit()

        #expect(model.result == nil)
        #expect(model.errorMessage != nil)
        #expect(model.isSaving == false)
    }

    // MARK: Feedback

    @Test("feedback finds the marking for a question, including its explanation")
    func feedbackForQuestion() async {
        let (model, _) = makeSUT()
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)
        await model.submit()

        #expect(model.feedback(for: Constants.questionOne)?.wasCorrect == true)
        #expect(model.feedback(for: Constants.questionTwo)?.explanation == "Because.")
    }

    @Test("no feedback for a question that was not marked")
    func noFeedbackForUnmarkedQuestion() async {
        let (model, _) = makeSUT()
        model.choose(question: Constants.questionOne, option: Constants.optionA)
        model.choose(question: Constants.questionTwo, option: Constants.optionB)
        await model.submit()

        #expect(model.feedback(for: UUID()) == nil)
    }

    private actor Received {
        private(set) var value: LearnerModuleResponse?
        func set(_ module: LearnerModuleResponse) { value = module }
    }
}
