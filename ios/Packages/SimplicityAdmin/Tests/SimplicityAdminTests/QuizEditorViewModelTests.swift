import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("QuizEditorViewModel", .serialized)
@MainActor
final class QuizEditorViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId, id: UUID(), profileCompleted: true,
            status: .active
        )
    }

    nonisolated private static func module() -> AuthoredModuleResponse {
        AuthoredModuleResponse(
            draft: VersionResponse(questions: [], status: "DRAFT", versionId: UUID()),
            moduleId: Constants.moduleId,
            title: "A module"
        )
    }

    private func makeSUT(saveFails: Bool = false) -> (QuizEditorViewModel, CapturedQuestions) {
        let captured = CapturedQuestions()
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).module(orgId: .any, moduleId: .any).willReturn(Self.module())
        if saveFails {
            given(authoring).replaceQuiz(orgId: .any, moduleId: .any, questions: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(authoring).replaceQuiz(orgId: .any, moduleId: .any, questions: .any)
                .willProduce { _, _, questions in
                    captured.value = questions
                    return Self.module()
                }
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.authoringService.register { authoring }
        Container.shared.sessionService.register { session }
        return (QuizEditorViewModel(moduleId: Constants.moduleId), captured)
    }

    /// A question that would pass validation, so each test can break exactly one thing.
    private func addValidQuestion(to model: QuizEditorViewModel) {
        model.addQuestion()
        let question = model.questions[model.questions.count - 1]
        model.updatePrompt(id: question.id, prompt: "Is this valid?")
        model.updateOption(question: question.id, option: question.options[0].id, label: "Yes")
        model.updateOption(question: question.id, option: question.options[1].id, label: "No")
        model.setCorrect(question: question.id, option: question.options[0].id)
    }

    // MARK: Validation

    @Test("a question needs a prompt")
    func needsPrompt() async {
        let (model, _) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        model.updatePrompt(id: model.questions[0].id, prompt: "  ")

        #expect(model.validationMessage != nil)
        #expect(model.canSave == false)
    }

    @Test("a question needs at least two options — one option is not a question")
    func needsTwoOptions() async {
        let (model, _) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        let question = model.questions[0]
        model.deleteOption(question: question.id, option: question.options[1].id)

        #expect(model.validationMessage != nil)
    }

    @Test("every option needs a label")
    func needsOptionLabels() async {
        let (model, _) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        let question = model.questions[0]
        model.updateOption(question: question.id, option: question.options[1].id, label: "  ")

        #expect(model.validationMessage != nil)
    }

    @Test("a question with no correct option is refused")
    func needsACorrectOption() async {
        let (model, _) = makeSUT()
        await model.load()
        model.addQuestion()
        let question = model.questions[0]
        model.updatePrompt(id: question.id, prompt: "Which?")
        model.updateOption(question: question.id, option: question.options[0].id, label: "Yes")
        model.updateOption(question: question.id, option: question.options[1].id, label: "No")

        #expect(model.validationMessage != nil)
    }

    @Test("marking one option correct unmarks the previous, so two can never both be")
    func onlyOneCorrect() async {
        let (model, _) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        let question = model.questions[0]

        model.setCorrect(question: question.id, option: question.options[1].id)

        let updated = model.questions[0]
        #expect(updated.options.filter(\.isCorrect).count == 1)
        #expect(updated.options[1].isCorrect)
    }

    @Test("the message names the offending question by position")
    func messageNamesPosition() async {
        // "A question is invalid" in a list of nine is useless to somebody trying to fix it.
        let (model, _) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        addValidQuestion(to: model)
        model.updatePrompt(id: model.questions[1].id, prompt: "")

        #expect(model.validationMessage?.contains("2") == true)
    }

    // MARK: Saving

    @Test("a valid quiz sends every question")
    func savesEveryQuestion() async {
        let (model, captured) = makeSUT()
        await model.load()
        addValidQuestion(to: model)
        addValidQuestion(to: model)

        await model.save()

        #expect(captured.value?.count == 2)
        #expect(model.isDirty == false)
    }

    @Test("an empty quiz saves, because a module may legitimately have none")
    func emptyQuizIsAllowed() async {
        let (model, captured) = makeSUT()
        await model.load()

        await model.save()

        #expect(captured.value?.isEmpty == true)
    }

    @Test("an invalid quiz is refused before the request")
    func invalidQuizNotSent() async {
        let (model, captured) = makeSUT()
        await model.load()
        model.addQuestion()

        await model.save()

        #expect(captured.value == nil)
        #expect(model.errorMessage != nil)
    }

    @Test("a failed save stays dirty")
    func failedSaveStaysDirty() async {
        let (model, _) = makeSUT(saveFails: true)
        await model.load()
        addValidQuestion(to: model)

        await model.save()

        #expect(model.isDirty)
        #expect(model.errorMessage != nil)
    }

    /// Mockable's `willProduce` is synchronous, so this cannot be an actor.
    private final class CapturedQuestions: @unchecked Sendable {
        private let lock = NSLock()
        private var stored: [QuestionInput]?

        var value: [QuestionInput]? {
            get { lock.withLock { stored } }
            set { lock.withLock { stored = newValue } }
        }
    }
}
