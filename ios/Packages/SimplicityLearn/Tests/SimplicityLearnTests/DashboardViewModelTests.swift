import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityLearn

@Suite("DashboardViewModel", .serialized)
@MainActor
final class DashboardViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
    }

    nonisolated private static func module(
        _ title: String,
        status: AssignedModuleResponse.Status
    ) -> AssignedModuleResponse {
        AssignedModuleResponse(moduleId: UUID(), status: status, title: title)
    }

    nonisolated private static func user(fullName: String?) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId,
            fullName: fullName,
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        modules: [AssignedModuleResponse],
        fullName: String? = "Ana Whitiora Reid"
    ) -> DashboardViewModel {
        let learning = MockLearningService(policy: .relaxed)
        given(learning).assignedModules(orgId: .any).willReturn(modules)
        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user(fullName: fullName))

        Container.shared.learningService.register { learning }
        Container.shared.sessionService.register { session }
        return DashboardViewModel()
    }

    // MARK: Outstanding and next

    @Test("outstanding excludes what is complete and keeps the rest")
    func outstandingExcludesCompleted() async {
        let model = makeSUT(modules: [
            Self.module("done", status: .completed),
            Self.module("started", status: .inProgress),
            Self.module("fresh", status: .notStarted),
            Self.module("again", status: .needsRedoing)
        ])

        await model.load()

        #expect(model.outstanding.map(\.title) == ["started", "fresh", "again"])
    }

    @Test("next prefers something already underway over something not started")
    func nextPrefersInProgress() async {
        let model = makeSUT(modules: [
            Self.module("fresh", status: .notStarted),
            Self.module("started", status: .inProgress)
        ])

        await model.load()

        #expect(model.next?.title == "started")
    }

    @Test("next falls back to the first outstanding module when nothing is underway")
    func nextFallsBackToFirst() async {
        let model = makeSUT(modules: [
            Self.module("first", status: .notStarted),
            Self.module("second", status: .notStarted)
        ])

        await model.load()

        #expect(model.next?.title == "first")
    }

    @Test("next is nothing when everything is finished")
    func nextIsNilWhenAllComplete() async {
        let model = makeSUT(modules: [Self.module("done", status: .completed)])

        await model.load()

        #expect(model.next == nil)
    }

    // MARK: Lede

    @Test("says nothing is assigned when nothing is")
    func ledeWithNothingAssigned() async {
        let model = makeSUT(modules: [])

        await model.load()

        #expect(model.lede == "Nothing has been assigned to your teams yet.")
    }

    @Test("says everything is finished when it is")
    func ledeWhenAllComplete() async {
        let model = makeSUT(modules: [Self.module("done", status: .completed)])

        await model.load()

        #expect(model.lede == "You have finished everything assigned to you.")
    }

    @Test("uses the singular for one outstanding module")
    func ledeSingular() async {
        let model = makeSUT(modules: [Self.module("one", status: .notStarted)])

        await model.load()

        #expect(model.lede == "You have 1 module outstanding.")
    }

    @Test("uses the plural for more than one")
    func ledePlural() async {
        let model = makeSUT(modules: [
            Self.module("one", status: .notStarted),
            Self.module("two", status: .notStarted),
            Self.module("three", status: .needsRedoing)
        ])

        await model.load()

        #expect(model.lede == "You have 3 modules outstanding.")
    }

    // MARK: Greeting

    @Test("greets by first name")
    func firstName() async {
        let model = makeSUT(modules: [], fullName: "Ana Whitiora Reid")

        await model.load()

        #expect(model.firstName == "Ana")
    }

    @Test("greets with nothing rather than crashing when there is no name yet")
    func firstNameWhenMissing() async {
        let model = makeSUT(modules: [], fullName: nil)

        await model.load()

        #expect(model.firstName.isEmpty)
    }
}
