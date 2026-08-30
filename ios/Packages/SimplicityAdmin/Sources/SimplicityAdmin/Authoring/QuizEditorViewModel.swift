import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

public struct DraftOption: Identifiable, Equatable, Sendable {
    public let id: UUID
    public var label: String
    public var isCorrect: Bool

    public init(id: UUID = UUID(), label: String = "", isCorrect: Bool = false) {
        self.id = id
        self.label = label
        self.isCorrect = isCorrect
    }
}

public struct DraftQuestion: Identifiable, Equatable, Sendable {
    public let id: UUID
    public var prompt: String
    public var explanation: String
    public var options: [DraftOption]

    public init(
        id: UUID = UUID(),
        prompt: String = "",
        explanation: String = "",
        options: [DraftOption] = [DraftOption(), DraftOption()]
    ) {
        self.id = id
        self.prompt = prompt
        self.explanation = explanation
        self.options = options
    }
}

@Observable
@MainActor
public final class QuizEditorViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authoringService) private var authoring
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public let moduleId: UUID

    public private(set) var questions: [DraftQuestion] = []
    public private(set) var isLoading = false
    public private(set) var isSaving = false
    public private(set) var isDirty = false
    public private(set) var errorMessage: String?

    private var orgId: UUID?

    /// Names the offending question by its position. "A question is invalid" in a list of nine is
    /// useless to somebody trying to fix it.
    public var validationMessage: String? {
        for (index, question) in questions.enumerated() {
            let number = index + 1

            if question.prompt.trimmingCharacters(in: .whitespaces).isEmpty {
                return message("quiz_editor_needs_prompt", number)
            }
            if question.options.count < 2 {
                return message("quiz_editor_needs_two_options", number)
            }
            if question.options.contains(where: {
                $0.label.trimmingCharacters(in: .whitespaces).isEmpty
            }) {
                return message("quiz_editor_needs_option_labels", number)
            }
            if question.options.filter(\.isCorrect).count != 1 {
                return message("quiz_editor_needs_one_correct", number)
            }
        }
        return nil
    }

    public var canSave: Bool {
        validationMessage == nil && !isSaving
    }

    // MARK: Init

    public init(moduleId: UUID) {
        self.moduleId = moduleId
    }

    // MARK: Functions

    public func load() async {
        orgId = await session.current?.activeOrganisationId
        guard let orgId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let module = try await authoring.module(orgId: orgId, moduleId: moduleId)
            adopt(module)
        } catch {
            errorMessage = String(localized: "authoring_load_failed", bundle: .module)
        }
    }

    public func addQuestion() {
        questions.append(DraftQuestion())
        isDirty = true
    }

    public func updatePrompt(id: UUID, prompt: String) {
        guard let index = questions.firstIndex(where: { $0.id == id }) else { return }
        questions[index].prompt = prompt
        isDirty = true
    }

    public func updateExplanation(id: UUID, explanation: String) {
        guard let index = questions.firstIndex(where: { $0.id == id }) else { return }
        questions[index].explanation = explanation
        isDirty = true
    }

    public func addOption(to questionId: UUID) {
        guard let index = questions.firstIndex(where: { $0.id == questionId }) else { return }
        questions[index].options.append(DraftOption())
        isDirty = true
    }

    public func updateOption(question questionId: UUID, option optionId: UUID, label: String) {
        guard
            let questionIndex = questions.firstIndex(where: { $0.id == questionId }),
            let optionIndex = questions[questionIndex].options.firstIndex(where: {
                $0.id == optionId
            })
        else {
            return
        }
        questions[questionIndex].options[optionIndex].label = label
        isDirty = true
    }

    /// Marking one correct unmarks the rest. The server requires exactly one, and letting an
    /// author tick two only to be refused later is a worse way to learn that.
    public func setCorrect(question questionId: UUID, option optionId: UUID) {
        guard let questionIndex = questions.firstIndex(where: { $0.id == questionId }) else {
            return
        }
        for index in questions[questionIndex].options.indices {
            questions[questionIndex].options[index].isCorrect =
                questions[questionIndex].options[index].id == optionId
        }
        isDirty = true
    }

    public func deleteQuestion(id: UUID) {
        questions.removeAll { $0.id == id }
        isDirty = true
    }

    public func deleteOption(question questionId: UUID, option optionId: UUID) {
        guard let index = questions.firstIndex(where: { $0.id == questionId }) else { return }
        questions[index].options.removeAll { $0.id == optionId }
        isDirty = true
    }

    public func save() async {
        guard let orgId, !isSaving else { return }

        if let message = validationMessage {
            errorMessage = message
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        // An empty array is meaningful: it removes the quiz. A module may legitimately have none.
        let inputs = questions.map { question in
            QuestionInput(
                explanation: question.explanation.isEmpty ? nil : question.explanation,
                options: question.options.map {
                    OptionInput(correct: $0.isCorrect, label: $0.label)
                },
                prompt: question.prompt
            )
        }

        do {
            _ = try await authoring.replaceQuiz(
                orgId: orgId, moduleId: moduleId, questions: inputs
            )
            isDirty = false
        } catch {
            errorMessage = String(localized: "authoring_save_failed", bundle: .module)
        }
    }

    // MARK: Private

    private func message(_ key: String.LocalizationValue, _ number: Int) -> String {
        String(format: String(localized: key, bundle: .module), number)
    }

    private func adopt(_ module: AuthoredModuleResponse) {
        let version = module.draft ?? module.published
        questions = (version?.questions ?? []).map { authored in
            DraftQuestion(
                prompt: authored.prompt ?? .empty,
                explanation: authored.explanation ?? .empty,
                options: (authored.options ?? []).map {
                    DraftOption(label: $0.label ?? .empty, isCorrect: $0.correct ?? false)
                }
            )
        }
        isDirty = false
    }
}
