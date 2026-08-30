import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("PublishViewModel", .serialized)
@MainActor
final class PublishViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
        static let assignedTeam = UUID()
        static let otherTeam = UUID()
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

    nonisolated private static func module(withSections: Bool = true) -> AuthoredModuleResponse {
        AuthoredModuleResponse(
            assignedTeamIds: [Constants.assignedTeam],
            draft: VersionResponse(
                sections: withSections
                    ? [SectionResponse(body: "b", sectionId: UUID(), title: "One")]
                    : [],
                status: "DRAFT",
                versionId: UUID()
            ),
            moduleId: Constants.moduleId,
            title: "A module"
        )
    }

    private func makeSUT(
        withSections: Bool = true,
        publishFails: Bool = false
    ) -> SUT {
        let captured = CapturedPublish()
        let authoring = MockAuthoringService(policy: .relaxed)
        given(authoring).module(orgId: .any, moduleId: .any)
            .willReturn(Self.module(withSections: withSections))
        given(authoring).assignTeams(orgId: .any, moduleId: .any, teamIds: .any)
            .willProduce { _, _, teamIds in
                captured.teamIds = teamIds
                return Self.module()
            }
        if publishFails {
            given(authoring).publish(orgId: .any, moduleId: .any, supersedesCompletions: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(authoring).publish(orgId: .any, moduleId: .any, supersedesCompletions: .any)
                .willProduce { _, _, supersedes in
                    captured.supersedes = supersedes
                    return Self.module()
                }
        }

        let organisations = MockOrganisationService(policy: .relaxed)
        given(organisations).teams(orgId: .any).willReturn([
            TeamResponse(id: Constants.assignedTeam, name: "Ward"),
            TeamResponse(id: Constants.otherTeam, name: "Clinic")
        ])

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.authoringService.register { authoring }
        Container.shared.organisationService.register { organisations }
        Container.shared.sessionService.register { session }
        return SUT(
            model: PublishViewModel(moduleId: Constants.moduleId),
            captured: captured,
            authoring: authoring
        )
    }

    private struct SUT {
        let model: PublishViewModel
        let captured: CapturedPublish
        let authoring: MockAuthoringService
    }

    // MARK: Assignment

    @Test("teams already assigned start selected, so saving unchanged changes nothing")
    func preselectsAssignedTeams() async {
        let sut = makeSUT()

        await sut.model.load()

        #expect(sut.model.selectedTeamIds == [Constants.assignedTeam])
    }

    @Test("deselecting everything sends an empty list, which is how a module is unassigned")
    func deselectingSendsEmpty() async {
        let sut = makeSUT()
        await sut.model.load()

        sut.model.toggle(Constants.assignedTeam)
        await sut.model.assign()

        #expect(sut.captured.teamIds?.isEmpty == true)
    }

    @Test("selecting another team sends both")
    func selectingSendsBoth() async {
        let sut = makeSUT()
        await sut.model.load()

        sut.model.toggle(Constants.otherTeam)
        await sut.model.assign()

        #expect(sut.captured.teamIds?.count == 2)
    }

    // MARK: Publishing

    @Test("a module with no sections is refused before the request")
    func refusesEmptyModule() async {
        let sut = makeSUT(withSections: false)
        await sut.model.load()

        await sut.model.publish()

        #expect(sut.model.errorMessage != nil)
        verify(sut.authoring)
            .publish(orgId: .any, moduleId: .any, supersedesCompletions: .any).called(0)
    }

    @Test("publishing without superseding leaves completions alone")
    func publishesWithoutSuperseding() async {
        let sut = makeSUT()
        await sut.model.load()

        await sut.model.publish()

        #expect(sut.captured.supersedes == false)
        #expect(sut.model.didPublish)
    }

    @Test("publishing with superseding sends everyone back through the module")
    func publishesWithSuperseding() async {
        // Its own test rather than a parameter: getting this backwards either un-completes an
        // entire organisation's training or fails to, and a failure should name which.
        let sut = makeSUT()
        await sut.model.load()
        sut.model.supersedesCompletions = true

        await sut.model.publish()

        #expect(sut.captured.supersedes == true)
    }

    @Test("a failed publish does not claim success")
    func failedPublish() async {
        let sut = makeSUT(publishFails: true)
        await sut.model.load()

        await sut.model.publish()

        #expect(sut.model.didPublish == false)
        #expect(sut.model.errorMessage != nil)
    }

    /// Mockable's `willProduce` is synchronous, so this cannot be an actor.
    private final class CapturedPublish: @unchecked Sendable {
        private let lock = NSLock()
        private var storedTeams: [UUID]?
        private var storedSupersedes: Bool?

        var teamIds: [UUID]? {
            get { lock.withLock { storedTeams } }
            set { lock.withLock { storedTeams = newValue } }
        }

        var supersedes: Bool? {
            get { lock.withLock { storedSupersedes } }
            set { lock.withLock { storedSupersedes = newValue } }
        }
    }
}
