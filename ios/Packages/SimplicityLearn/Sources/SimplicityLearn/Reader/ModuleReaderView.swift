import SimplicityApi
import SimplicityDesign
import SwiftUI

public struct ModuleReaderView: View {

    // MARK: Properties

    @State private var model: ModuleReaderViewModel

    // MARK: Init

    public init(moduleId: UUID) {
        self._model = State(initialValue: ModuleReaderViewModel(moduleId: moduleId))
    }

    // MARK: SwiftUI

    public var body: some View {
        content
            .navigationTitle(model.module?.title ?? "")
            .inlineTitle()
            .task { await model.load() }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading, model.module == nil {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.module == nil, let message = model.errorMessage {
            ContentUnavailableView {
                Label(message, systemImage: "wifi.exclamationmark")
            } actions: {
                Button("Try again") { Task { await model.load() } }
            }
        } else if let module = model.module {
            reader(module)
        }
    }

    private func reader(_ module: LearnerModuleResponse) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.x4) {
                    summary(module)

                    ErrorBanner(message: model.errorMessage)

                    ForEach(model.sections, id: \.sectionId) { section in
                        SectionView(
                            section: section,
                            isRead: model.isRead(section),
                            isSaving: model.isSaving,
                            onMarkRead: { Task { await model.markRead(section) } }
                        )
                        .id(section.sectionId)

                        Divider()
                    }

                    if let quiz = model.quiz {
                        QuizView(
                            moduleId: model.moduleId,
                            quiz: quiz,
                            isUnlocked: model.allSectionsRead,
                            onModuleChanged: { model.moduleChanged($0) }
                        )
                    }
                }
                .padding(Spacing.x5)
            }
            .onAppear {
                // Resuming matters: a module is several sections long and nobody finishes one in
                // a sitting.
                guard let target = model.firstUnreadSectionId else { return }
                proxy.scrollTo(target, anchor: .top)
            }
        }
    }

    private func summary(_ module: LearnerModuleResponse) -> some View {
        VStack(alignment: .leading, spacing: Spacing.x2) {
            if let summary = module.summary, !summary.isEmpty {
                Text(verbatim: summary)
                    .font(.brandBody)
                    .foregroundStyle(Color.brandTextSecondary)
            }

            Text(verbatim: model.statusLabel)
                .font(.brandCaption)
                .padding(.horizontal, Spacing.x2)
                .padding(.vertical, Spacing.x1)
                .background(
                    module.status == .completed
                        ? Color.brandPrimary.opacity(0.12)
                        : Color.brandSurface
                )
                .clipShape(Capsule())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension View {

    /// `navigationBarTitleDisplayMode` does not exist on macOS, and the package stays buildable
    /// there so its view models can be tested with `swift test`.
    @ViewBuilder
    func inlineTitle() -> some View {
        #if os(iOS)
        navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}
