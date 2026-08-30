import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("MembersViewModel", .serialized)
@MainActor
final class MembersViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let meId = UUID()
        static let colleagueId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func member(
        id: UUID,
        name: String,
        role: OrgMemberResponse.OrgRole = .orgMember
    ) -> OrgMemberResponse {
        OrgMemberResponse(
            email: "\(name.lowercased())@example.com",
            fullName: name,
            orgRole: role,
            userId: id
        )
    }

    nonisolated private static func user() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: Constants.orgId,
            id: Constants.meId,
            profileCompleted: true,
            status: .active
        )
    }

    private func makeSUT(
        members: [OrgMemberResponse]? = nil,
        listFails: Bool = false,
        mutationFails: Bool = false
    ) -> (MembersViewModel, MockOrganisationService) {
        let resolved = members ?? [
            Self.member(id: Constants.meId, name: "Me", role: .orgAdmin),
            Self.member(id: Constants.colleagueId, name: "Colleague")
        ]
        let organisations = MockOrganisationService(policy: .relaxed)
        if listFails {
            given(organisations).members(orgId: .any).willThrow(TestError.unreachable)
        } else {
            given(organisations).members(orgId: .any).willReturn(resolved)
        }
        if mutationFails {
            given(organisations).removeMember(orgId: .any, userId: .any)
                .willThrow(TestError.unreachable)
            given(organisations).changeRole(orgId: .any, userId: .any, role: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(organisations).removeMember(orgId: .any, userId: .any).willReturn(())
            given(organisations).changeRole(orgId: .any, userId: .any, role: .any).willReturn(
                Self.member(id: Constants.colleagueId, name: "Colleague", role: .orgAdmin)
            )
        }

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.organisationService.register { organisations }
        Container.shared.sessionService.register { session }
        return (MembersViewModel(), organisations)
    }

    @Test("members come back in the server's order")
    func listsMembers() async {
        let (model, _) = makeSUT()

        await model.load()

        #expect(model.members.map(\.fullName) == ["Me", "Colleague"])
    }

    @Test("your own row is recognised, so it can hide remove and the role picker")
    func recognisesSelf() async {
        let (model, _) = makeSUT()
        await model.load()

        #expect(model.isSelf(Self.member(id: Constants.meId, name: "Me")))
        #expect(model.isSelf(Self.member(id: Constants.colleagueId, name: "Colleague")) == false)
    }

    @Test("changing a role takes the server's answer rather than assuming it worked")
    func changeRoleUsesServerResponse() async {
        let (model, _) = makeSUT()
        await model.load()

        await model.changeRole(model.members[1], to: .orgAdmin)

        #expect(model.members[1].orgRole == .orgAdmin)
    }

    @Test("a failed role change does not show the new role")
    func failedRoleChange() async {
        let (model, _) = makeSUT(mutationFails: true)
        await model.load()

        await model.changeRole(model.members[1], to: .orgAdmin)

        #expect(model.members[1].orgRole == .orgMember)
        #expect(model.errorMessage != nil)
    }

    @Test("removing takes the member out of the list")
    func removesMember() async {
        let (model, _) = makeSUT()
        await model.load()

        await model.remove(model.members[1])

        #expect(model.members.contains { $0.userId == Constants.colleagueId } == false)
    }

    @Test("a failed removal keeps them listed, because a list that lies is worse than an error")
    func failedRemoval() async {
        let (model, _) = makeSUT(mutationFails: true)
        await model.load()

        await model.remove(model.members[1])

        #expect(model.members.contains { $0.userId == Constants.colleagueId })
        #expect(model.errorMessage != nil)
        #expect(model.isBusy == false)
    }

    @Test("a failed load says so rather than showing an empty organisation")
    func failedLoad() async {
        let (model, _) = makeSUT(listFails: true)

        await model.load()

        #expect(model.members.isEmpty)
        #expect(model.errorMessage != nil)
    }
}
