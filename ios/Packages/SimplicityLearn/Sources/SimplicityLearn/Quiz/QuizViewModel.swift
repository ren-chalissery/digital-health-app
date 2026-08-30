import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class QuizViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.learningService) private var learning
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let moduleId: UUID
    public let quiz: QuizResponse

    public private(set) var chosen: [UUID: UUID] = [:]
    public private(set) var result: AttemptResultResponse?
    public private(set) var isSaving = false
    public private(set) var errorMessage: String?

    /// Passing may have completed the module, which only the reader knows about.
    @ObservationIgnored public var onModuleChanged: ((LearnerModuleResponse) -> Void)?

    public var questions: [QuizQuestionResponse] {
        quiz.questions ?? []
    }

    public var allAnswered: Bool {
        !questions.isEmpty && questions.allSatisfy { question in
            guard let questionId = question.questionId else { return false }
            return chosen[questionId] != nil
        }
    }

    // MARK: Init

    public init(moduleId: UUID, quiz: QuizResponse) {
        self.moduleId = moduleId
        self.quiz = quiz
    }

    // MARK: Functions

    public func choose(question: UUID, option: UUID) {
        chosen[question] = option
    }

    public func feedback(for question: UUID) -> MarkedQuestion? {
        result?.questions?.first { $0.questionId == question }
    }

    public func submit() async {
        // A second press after a result means "try again", so the previous marking is cleared
        // rather than left beside answers it no longer describes.
        if result != nil {
            result = nil
            chosen = [:]
            return
        }

        guard allAnswered, !isSaving else { return }
        guard
            let orgId = await session.current?.activeOrganisationId
        else {
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        let answers = chosen.map { AnswerInput(optionId: $0.value, questionId: $0.key) }

        do {
            result = try await learning.submitAttempt(
                orgId: orgId,
                moduleId: moduleId,
                answers: answers
            )
            let updated = try await learning.module(orgId: orgId, moduleId: moduleId)
            onModuleChanged?(updated)
        } catch {
            errorMessage = String(localized: "quiz_submit_failed", bundle: .module)
        }
    }
}
