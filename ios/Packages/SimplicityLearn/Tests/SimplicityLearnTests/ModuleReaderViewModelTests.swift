import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityLearn

@Suite("ModuleReaderViewModel", .serialized)
@MainActor
final class ModuleReaderViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
        static let firstSection = UUID()
        static let secondSection = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func section(_ id: UUID, title: String) -> SectionResponse {
        SectionResponse(body: "Body of \(title)", sectionId: id, title: title)
    }

    nonisolated private static func module(
        completed: [UUID] = [],
        hasQuiz: Bool = false,
        sections: [SectionResponse] = [
            section(Constants.firstSection, title: "One"),
            section(Constants.secondSection, title: "Two")
        ],
        status: LearnerModuleResponse.Status = .notStarted
    ) -> LearnerModuleResponse {
        LearnerModuleResponse(
            completedSectionIds: completed,
            hasQuiz: hasQuiz,
            moduleId: Constants.moduleId,
            sections: sections,
            status: status,
            title: "A module"
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

    /// Mockable honours the first `given` registered for a method, so the module a test wants has
    /// to be decided here rather than overridden afterwards.
    private func makeSUT(
        module: LearnerModuleResponse? = nil,
        moduleFails: Bool = false,
        configure: (MockLearningService) -> Void = { _ in }
    ) -> (ModuleReaderViewModel, MockLearningService) {
        let learning = MockLearningService(policy: .relaxed)
        if moduleFails {
            given(learning).module(orgId: .any, moduleId: .any).willThrow(TestError.unreachable)
        } else {
            given(learning).module(orgId: .any, moduleId: .any).willReturn(module ?? Self.module())
        }
        configure(learning)

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.learningService.register { learning }
        Container.shared.sessionService.register { session }
        return (ModuleReaderViewModel(moduleId: Constants.moduleId), learning)
    }

    // MARK: Reading

    @Test("a section is read only when the server says its id is complete")
    func isReadFollowsTheServer() async {
        let (model, _) = makeSUT(module: Self.module(completed: [Constants.firstSection]))
        await model.load()

        #expect(model.isRead(Self.section(Constants.firstSection, title: "One")))
        #expect(model.isRead(Self.section(Constants.secondSection, title: "Two")) == false)
    }

    @Test("a module with no sections is not all-read, which would unlock its quiz for nothing")
    func emptyModuleIsNotAllRead() async {
        let (model, _) = makeSUT(module: Self.module(sections: []))
        await model.load()

        #expect(model.allSectionsRead == false)
    }

    @Test("all sections read once every id is complete")
    func allSectionsRead() async {
        let (model, _) = makeSUT(
            module: Self.module(completed: [Constants.firstSection, Constants.secondSection])
        )
        await model.load()

        #expect(model.allSectionsRead)
    }

    @Test("resumption points at the first unread section")
    func firstUnreadSection() async {
        let (model, _) = makeSUT(module: Self.module(completed: [Constants.firstSection]))
        await model.load()

        #expect(model.firstUnreadSectionId == Constants.secondSection)
    }

    @Test("there is nothing to resume to once everything is read")
    func noUnreadSection() async {
        let (model, _) = makeSUT(
            module: Self.module(completed: [Constants.firstSection, Constants.secondSection])
        )
        await model.load()

        #expect(model.firstUnreadSectionId == nil)
    }

    // MARK: Quiz

    @Test("does not fetch a quiz for a module that has none")
    func fetchesQuizOnlyWhenPresent() async {
        let (model, learning) = makeSUT()
        await model.load()

        verify(learning).quiz(orgId: .any, moduleId: .any).called(0)
        #expect(model.quiz == nil)
    }

    @Test("fetches the quiz when the module has one")
    func fetchesQuizWhenPresent() async {
        let (model, learning) = makeSUT(module: Self.module(hasQuiz: true)) { learning in
            given(learning).quiz(orgId: .any, moduleId: .any)
                .willReturn(QuizResponse(attemptCount: 0, passed: false, questions: []))
        }
        await model.load()

        verify(learning).quiz(orgId: .any, moduleId: .any).called(1)
        #expect(model.quiz != nil)
    }

    // MARK: Marking read

    @Test("marking read replaces the module with the server's recomputed answer")
    func markReadUsesServerResponse() async {
        let (model, learning) = makeSUT { learning in
            given(learning).completeSection(orgId: .any, sectionId: .any).willReturn(
                Self.module(
                    completed: [Constants.firstSection, Constants.secondSection],
                    status: .completed
                )
            )
        }
        await model.load()

        await model.markRead(Self.section(Constants.firstSection, title: "One"))

        #expect(model.module?.status == .completed)
        verify(learning).completeSection(orgId: .any, sectionId: .any).called(1)
    }

    @Test("a failure says so and leaves the section unread, because silence would be a lie")
    func failedMarkRead() async {
        let (model, _) = makeSUT { learning in
            given(learning).completeSection(orgId: .any, sectionId: .any)
                .willThrow(TestError.unreachable)
        }
        await model.load()

        await model.markRead(Self.section(Constants.firstSection, title: "One"))

        #expect(model.errorMessage != nil)
        #expect(model.isRead(Self.section(Constants.firstSection, title: "One")) == false)
        #expect(model.isSaving == false)
    }

    // MARK: Failure to load

    @Test("a module that cannot be opened says so rather than showing an empty reader")
    func failedLoad() async {
        let (model, _) = makeSUT(moduleFails: true)

        await model.load()

        #expect(model.module == nil)
        #expect(model.errorMessage != nil)
        #expect(model.isLoading == false)
    }
}
