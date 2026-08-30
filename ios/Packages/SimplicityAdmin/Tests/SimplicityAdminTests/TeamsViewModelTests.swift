import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("TeamsViewModel", .serialized)
@MainActor
final class TeamsViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let teamId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func team(_ name: String) -> TeamResponse {
        TeamResponse(id: UUID(), name: name)
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId,
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        teams: [TeamResponse] = [],
        mutationFails: Bool = false
    ) -> (TeamsViewModel, MockOrganisationService) {
        let organisations = MockOrganisationService(policy: .relaxed)
        given(organisations).teams(orgId: .any).willReturn(teams)
        if mutationFails {
            given(organisations).createTeam(orgId: .any, name: .any, description: .any)
                .willThrow(TestError.unreachable)
            given(organisations).deleteTeam(orgId: .any, teamId: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(organisations).createTeam(orgId: .any, name: .any, description: .any)
                .willReturn(Self.team("Ward"))
            given(organisations).deleteTeam(orgId: .any, teamId: .any).willReturn(())
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.organisationService.register { organisations }
        Container.shared.sessionService.register { session }
        return (TeamsViewModel(), organisations)
    }

    @Test("a team needs a name")
    func cannotCreateWithoutName() async {
        let (model, organisations) = makeSUT()
        await model.load()

        await model.create()

        #expect(model.canCreate == false)
        verify(organisations).createTeam(orgId: .any, name: .any, description: .any).called(0)
    }

    @Test("creating appends the team and clears the form")
    func createAppendsAndClears() async {
        let (model, _) = makeSUT()
        await model.load()
        model.newTeamName = "Ward"
        model.newTeamDescription = "Inpatient"

        await model.create()

        #expect(model.teams.map(\.name) == ["Ward"])
        #expect(model.newTeamName.isEmpty)
        #expect(model.newTeamDescription.isEmpty)
    }

    @Test("a failed creation keeps the typing")
    func failedCreateKeepsFields() async {
        let (model, _) = makeSUT(mutationFails: true)
        await model.load()
        model.newTeamName = "Ward"

        await model.create()

        #expect(model.newTeamName == "Ward")
        #expect(model.errorMessage != nil)
    }

    @Test("deleting removes the team")
    func deleteRemovesTeam() async {
        let existing = Self.team("Ward")
        let (model, _) = makeSUT(teams: [existing])
        await model.load()

        await model.delete(existing)

        #expect(model.teams.isEmpty)
    }

    @Test("a failed deletion keeps the team listed")
    func failedDelete() async {
        let existing = Self.team("Ward")
        let (model, _) = makeSUT(teams: [existing], mutationFails: true)
        await model.load()

        await model.delete(existing)

        #expect(model.teams.count == 1)
        #expect(model.errorMessage != nil)
    }
}

@Suite("TeamDetailViewModel", .serialized)
@MainActor
final class TeamDetailViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let teamId = UUID()
        static let inTeam = UUID()
        static let notInTeam = UUID()
    }

    nonisolated private static func orgMember(_ id: UUID, _ name: String) -> OrgMemberResponse {
        OrgMemberResponse(email: "\(name)@example.com", fullName: name, userId: id)
    }

    nonisolated private static func teamMember(_ id: UUID, _ name: String) -> TeamMemberDetailResponse {
        TeamMemberDetailResponse(
            email: "\(name)@example.com", fullName: name, teamRole: .teamMember, userId: id
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

    private func makeSUT(
        teamMembers: [TeamMemberDetailResponse]? = nil,
        orgMembers: [OrgMemberResponse]? = nil
    ) -> (TeamDetailViewModel, MockOrganisationService) {
        let organisations = MockOrganisationService(policy: .relaxed)
        given(organisations).teamMembers(orgId: .any, teamId: .any)
            .willReturn(teamMembers ?? [Self.teamMember(Constants.inTeam, "Ana")])
        given(organisations).members(orgId: .any).willReturn(orgMembers ?? [
            Self.orgMember(Constants.inTeam, "Ana"),
            Self.orgMember(Constants.notInTeam, "Ben")
        ])
        given(organisations)
            .addTeamMember(orgId: .any, teamId: .any, userId: .any, role: .any).willReturn(())
        given(organisations)
            .removeTeamMember(orgId: .any, teamId: .any, userId: .any).willReturn(())

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.organisationService.register { organisations }
        Container.shared.sessionService.register { session }
        return (TeamDetailViewModel(teamId: Constants.teamId, teamName: "Ward"), organisations)
    }

    @Test("candidates exclude people already in the team")
    func candidatesExcludeExistingMembers() async {
        let (model, _) = makeSUT()

        await model.load()

        #expect(model.candidates.map(\.fullName) == ["Ben"])
    }

    @Test("there are no candidates when everybody is already in the team")
    func noCandidatesWhenAllPresent() async {
        let (model, _) = makeSUT(
            teamMembers: [Self.teamMember(Constants.inTeam, "Ana")],
            orgMembers: [Self.orgMember(Constants.inTeam, "Ana")]
        )

        await model.load()

        #expect(model.candidates.isEmpty)
    }

    @Test("removing takes the person out of the team")
    func removeTakesOut() async {
        let (model, _) = makeSUT()
        await model.load()

        await model.remove(model.members[0])

        #expect(model.members.isEmpty)
    }
}
