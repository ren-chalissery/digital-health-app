import SimplicityDesign
import SwiftUI

public struct QuizEditorView: View {

    // MARK: Properties

    @State private var model: QuizEditorViewModel

    // MARK: Init

    public init(moduleId: UUID) {
        self._model = State(initialValue: QuizEditorViewModel(moduleId: moduleId))
    }

    // MARK: SwiftUI

    public var body: some View {
        List {
            ErrorBanner(message: model.errorMessage)

            if model.questions.isEmpty {
                Text("quiz_editor_empty", bundle: .module)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            ForEach(model.questions) { question in
                questionEditor(question)
            }

            Button {
                model.addQuestion()
            } label: {
                Label {
                    Text("quiz_editor_add_question", bundle: .module)
                } icon: {
                    Image(systemName: "plus.circle")
                }
            }
            .accessibilityIdentifier("add-question")

            // The validation message names the offending question, so it belongs where it can be
            // read alongside them rather than only appearing on a failed save.
            if let message = model.validationMessage, !model.questions.isEmpty {
                Text(verbatim: message)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandDanger)
            }
        }
        .navigationTitle(Text("quiz_editor_title", bundle: .module))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task { await model.save() }
                } label: {
                    Text(model.isSaving ? "authoring_saving" : "authoring_save", bundle: .module)
                }
                .disabled(!model.canSave)
                .accessibilityIdentifier("save-quiz")
            }
        }
        .task { await model.load() }
    }

    // MARK: Private

    private func questionEditor(_ question: DraftQuestion) -> some View {
        Section {
            TextField(
                String(localized: "quiz_editor_prompt", bundle: .module),
                text: Binding(
                    get: { question.prompt },
                    set: { model.updatePrompt(id: question.id, prompt: $0) }
                ),
                axis: .vertical
            )
            .font(.brandBody.weight(.semibold))

            ForEach(question.options) { option in
                optionRow(question: question, option: option)
            }

            Button {
                model.addOption(to: question.id)
            } label: {
                Text("quiz_editor_add_option", bundle: .module).font(.brandCaption)
            }

            TextField(
                String(localized: "quiz_editor_explanation", bundle: .module),
                text: Binding(
                    get: { question.explanation },
                    set: { model.updateExplanation(id: question.id, explanation: $0) }
                ),
                axis: .vertical
            )
            .font(.brandCaption)

            Button(role: .destructive) {
                model.deleteQuestion(id: question.id)
            } label: {
                Text(verbatim: "Delete question").font(.brandCaption)
            }
        }
    }

    private func optionRow(question: DraftQuestion, option: DraftOption) -> some View {
        HStack(spacing: Spacing.x2) {
            Button {
                model.setCorrect(question: question.id, option: option.id)
            } label: {
                Image(systemName: option.isCorrect ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(option.isCorrect ? Color.brandPrimary : Color.brandTextSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: "Mark correct"))

            TextField(
                String(localized: "quiz_editor_option", bundle: .module),
                text: Binding(
                    get: { option.label },
                    set: {
                        model.updateOption(question: question.id, option: option.id, label: $0)
                    }
                )
            )

            Button(role: .destructive) {
                model.deleteOption(question: question.id, option: option.id)
            } label: {
                Image(systemName: "minus.circle")
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: "Remove option"))
        }
    }
}
