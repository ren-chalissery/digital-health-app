import SimplicityApi
import SimplicityDesign
import SwiftUI

struct QuizView: View {

    // MARK: Properties

    @State private var model: QuizViewModel
    private let isUnlocked: Bool

    // MARK: Init

    init(
        moduleId: UUID,
        quiz: QuizResponse,
        isUnlocked: Bool,
        onModuleChanged: @escaping (LearnerModuleResponse) -> Void
    ) {
        let model = QuizViewModel(moduleId: moduleId, quiz: quiz)
        model.onModuleChanged = onModuleChanged
        self._model = State(initialValue: model)
        self.isUnlocked = isUnlocked
    }

    // MARK: SwiftUI

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.x4) {
            Text("quiz_title", bundle: .module)
                .font(.brandTitle)
                .accessibilityAddTraits(.isHeader)

            if !isUnlocked {
                Text("quiz_locked", bundle: .module)
                    .font(.brandBody)
                    .foregroundStyle(Color.brandTextSecondary)
            } else {
                Text("quiz_intro", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)

                ForEach(model.questions, id: \.questionId) { question in
                    questionView(question)
                }

                ErrorBanner(message: model.errorMessage)

                PrimaryButton(title: submitTitle, isLoading: model.isSaving) {
                    Task { await model.submit() }
                }
                .disabled(!model.allAnswered && model.result == nil)
                .accessibilityIdentifier("quiz-submit")

                if let result = model.result {
                    Text(
                        String(
                            format: String(localized: "quiz_score", bundle: .module),
                            result.correctCount ?? 0,
                            result.questionCount ?? 0,
                            result.attemptNumber ?? 0
                        )
                    )
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
                }
            }
        }
    }

    // MARK: Private

    private var submitTitle: String {
        if model.isSaving {
            return String(localized: "quiz_checking", bundle: .module)
        }
        return model.result == nil
            ? String(localized: "quiz_submit", bundle: .module)
            : String(localized: "quiz_try_again", bundle: .module)
    }

    private func questionView(_ question: QuizQuestionResponse) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            Text(verbatim: question.prompt ?? "")
                .font(.brandBody.weight(.semibold))

            ForEach(question.options ?? [], id: \.optionId) { option in
                optionRow(question: question, option: option)
            }

            if let questionId = question.questionId, let marked = model.feedback(for: questionId) {
                feedbackView(marked)
            }
        }
        .padding(Spacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brandSurface)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
        // Read as one question with its options, rather than a flat run of unrelated labels.
        .accessibilityElement(children: .contain)
    }

    private func optionRow(
        question: QuizQuestionResponse,
        option: QuizOptionResponse
    ) -> some View {
        let isChosen = question.questionId.flatMap { model.chosen[$0] } == option.optionId

        return Button {
            if let questionId = question.questionId, let optionId = option.optionId {
                model.choose(question: questionId, option: optionId)
            }
        } label: {
            HStack(spacing: Spacing.x2) {
                Image(systemName: isChosen ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isChosen ? Color.brandPrimary : Color.brandTextSecondary)
                Text(verbatim: option.label ?? "")
                    .font(.brandBody)
                    .foregroundStyle(Color.brandTextPrimary)
                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isChosen ? [.isButton, .isSelected] : .isButton)
    }

    private func feedbackView(_ marked: MarkedQuestion) -> some View {
        let correct = marked.wasCorrect == true
        return VStack(alignment: .leading, spacing: Spacing.x1) {
            Text(correct ? "quiz_correct" : "quiz_incorrect", bundle: .module)
                .font(.brandCaption.weight(.semibold))
                .foregroundStyle(correct ? Color.brandPrimary : Color.brandDanger)

            if let explanation = marked.explanation, !explanation.isEmpty {
                Text(verbatim: explanation)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
