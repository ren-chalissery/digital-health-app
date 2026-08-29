import SimplicityApi
import SimplicityDesign
import SwiftUI

/// Framed as asking about the training, never as an assistant and never as advice.
///
/// This is a mental health product, and anything that sounds like it knows will be asked clinical
/// questions. The wording, the refusal and the citations all exist so a clinician can check rather
/// than trust.
public struct AskView: View {

    // MARK: Properties

    @State private var model = AskViewModel()
    @Environment(\.dismiss) private var dismiss
    private let onOpenModule: (UUID) -> Void

    // MARK: Init

    public init(onOpenModule: @escaping (UUID) -> Void) {
        self.onOpenModule = onOpenModule
    }

    // MARK: SwiftUI

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.x4) {
                    Text("ask_disclaimer", bundle: .module)
                        .font(.brandCaption)
                        .foregroundStyle(Color.brandTextSecondary)

                    field

                    PrimaryButton(title: submitTitle, isLoading: model.isAsking) {
                        Task { await model.ask() }
                    }
                    .disabled(!model.canAsk)
                    .accessibilityIdentifier("ask-submit")

                    ErrorBanner(message: model.errorMessage)

                    if let answer = model.answer {
                        answerView(answer)
                    }
                }
                .padding(Spacing.x5)
            }
            .navigationTitle(Text("ask_title", bundle: .module))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button { dismiss() } label: { Text("ask_close", bundle: .module) }
                }
            }
        }
    }

    // MARK: Private

    private var submitTitle: String {
        model.isAsking
            ? String(localized: "ask_asking", bundle: .module)
            : String(localized: "ask_submit", bundle: .module)
    }

    private var field: some View {
        TextField(
            String(localized: "ask_prompt", bundle: .module),
            text: $model.question,
            axis: .vertical
        )
        .font(.brandBody)
        .lineLimit(2...5)
        // Off for the same reason the journal editor's is: somebody asking about the training may
        // still type something clinical, and the keyboard learns from it.
        .autocorrectionDisabled()
        .padding(Spacing.x3)
        .background(Color.brandSurface)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
    }

    @ViewBuilder
    private func answerView(_ answer: AnswerResponse) -> some View {
        if answer.answered == true {
            VStack(alignment: .leading, spacing: Spacing.x3) {
                Text(verbatim: answer.answer ?? "")
                    .font(.brandBody)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if !model.citations.isEmpty {
                    citations
                }
            }
        } else {
            Text("ask_unanswered", bundle: .module)
                .font(.brandBody)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.x3)
                .background(Color.brandSurface)
                .clipShape(RoundedRectangle(cornerRadius: Spacing.x2))
        }
    }

    private var citations: some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            Text("ask_sources", bundle: .module)
                .font(.brandCaption.weight(.semibold))
                .foregroundStyle(Color.brandTextSecondary)

            ForEach(model.citations, id: \.moduleTitle) { citation in
                citationRow(citation)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// A module assigned to this clinician is tappable; one that is not is named without a link,
    /// so nobody is sent somewhere they cannot go. Within an organisation training is not secret,
    /// so naming it is fine — linking to it would not be.
    @ViewBuilder
    private func citationRow(_ citation: CitationResponse) -> some View {
        let label = String(
            format: String(localized: "ask_citation", bundle: .module),
            citation.moduleTitle ?? "",
            citation.sectionTitle ?? ""
        )

        if citation.assignedToYou == true, let moduleId = citation.moduleId {
            Button {
                dismiss()
                onOpenModule(moduleId)
            } label: {
                Text(verbatim: label)
                    .font(.brandCaption)
                    .foregroundStyle(Color.brandPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        } else {
            Text(verbatim: label)
                .font(.brandCaption)
                .foregroundStyle(Color.brandTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
