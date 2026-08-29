import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityLearn

@Suite("ModuleListViewModel", .serialized)
@MainActor
final class ModuleListViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func module(
        _ title: String,
        summary: String? = nil,
        status: AssignedModuleResponse.Status = .notStarted
    ) -> AssignedModuleResponse {
        AssignedModuleResponse(
            completedSectionCount: 0,
            moduleId: UUID(),
            sectionCount: 3,
            status: status,
            summary: summary,
            title: title
        )
    }

    nonisolated private static func user(orgId: UUID?) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: orgId,
            id: UUID(),
            organisations: orgId.map { [OrganisationMembershipResponse(name: "A Clinic", orgId: $0)] } ?? [],
            profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        modules: [AssignedModuleResponse] = [],
        orgId: UUID? = Constants.orgId,
        learning: MockLearningService? = nil
    ) -> (ModuleListViewModel, MockLearningService) {
        let service = learning ?? MockLearningService(policy: .relaxed)
        if learning == nil {
            given(service).assignedModules(orgId: .any).willReturn(modules)
        }
        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user(orgId: orgId))

        Container.shared.learningService.register { service }
        Container.shared.sessionService.register { session }
        return (ModuleListViewModel(), service)
    }

    // MARK: Loading

    @Test("without an active organisation it asks for nothing and does not spin forever")
    func noActiveOrganisation() async {
        let (model, service) = makeSUT(orgId: nil)

        await model.load()

        #expect(model.isLoading == false)
        #expect(model.modules.isEmpty)
        verify(service).assignedModules(orgId: .any).called(0)
    }

    @Test("exposes the modules in the server's order")
    func preservesOrder() async {
        let (model, _) = makeSUT(modules: [Self.module("zebra"), Self.module("apple")])

        await model.load()

        #expect(model.modules.map(\.title) == ["zebra", "apple"])
        #expect(model.errorMessage == nil)
    }

    @Test("a failure sets a message, so an empty list is not mistaken for nothing assigned")
    func failureIsDistinguishable() async {
        let service = MockLearningService(policy: .relaxed)
        given(service).assignedModules(orgId: .any).willThrow(TestError.unreachable)
        let (model, _) = makeSUT(learning: service)

        await model.load()

        #expect(model.modules.isEmpty)
        #expect(model.errorMessage != nil)
        #expect(model.isLoading == false)
    }

    // MARK: Search

    @Test("an empty search shows everything")
    func emptySearchShowsAll() async {
        let (model, _) = makeSUT(modules: [Self.module("one"), Self.module("two")])
        await model.load()

        #expect(model.visible.count == 2)
    }

    @Test("filters on title, ignoring case")
    func filtersOnTitleIgnoringCase() async {
        let (model, _) = makeSUT(modules: [Self.module("Trauma informed care"), Self.module("Risk")])
        await model.load()

        model.search = "TRAUMA"

        #expect(model.visible.map(\.title) == ["Trauma informed care"])
    }

    @Test("filters on the summary too, because titles are short")
    func filtersOnSummary() async {
        let (model, _) = makeSUT(modules: [
            Self.module("Module one", summary: "Covers de-escalation"),
            Self.module("Module two", summary: "Covers documentation")
        ])
        await model.load()

        model.search = "escalation"

        #expect(model.visible.map(\.title) == ["Module one"])
    }

    @Test("matching ignores diacritics, so searching Maori finds Māori")
    func matchingIgnoresDiacritics() async {
        let (model, _) = makeSUT(modules: [Self.module("Working with Māori")])
        await model.load()

        model.search = "Maori"

        #expect(model.visible.count == 1)
    }

    @Test("a search matching nothing shows nothing rather than everything")
    func noMatches() async {
        let (model, _) = makeSUT(modules: [Self.module("one")])
        await model.load()

        model.search = "nothing like it"

        #expect(model.visible.isEmpty)
    }
}
