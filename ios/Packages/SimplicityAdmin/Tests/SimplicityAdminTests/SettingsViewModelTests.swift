import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("SettingsViewModel", .serialized)
@MainActor
final class SettingsViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let adminOrg = UUID()
        static let memberOrg = UUID()
        /// Stable, so a member row can be recognised as the signed-in person.
        static let userId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    private struct SUT {
        let model: SettingsViewModel
        let session: MockSessionService
        let organisations: MockOrganisationService
    }

    nonisolated private static func user(
        active: UUID?,
        memberships: [OrganisationMembershipResponse]
    ) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: active,
            email: "clinician@example.com",
            fullName: "A Clinician",
            id: Constants.userId,
            organisations: memberships,
            profileCompleted: true,
            status: .active
        )
    }

    nonisolated private static func member(
        role: OrgMemberResponse.OrgRole,
        id: UUID
    ) -> OrgMemberResponse {
        OrgMemberResponse(
            email: "colleague@example.com",
            fullName: "A Colleague",
            membershipStatus: .active,
            orgRole: role,
            userId: id,
            userStatus: .active
        )
    }

    nonisolated private static func bothOrganisations() -> [OrganisationMembershipResponse] {
        [
            OrganisationMembershipResponse(
                name: "Clinic A", orgId: Constants.adminOrg, orgRole: .orgAdmin
            ),
            OrganisationMembershipResponse(
                name: "Clinic B", orgId: Constants.memberOrg, orgRole: .orgMember
            )
        ]
    }

    private func makeSUT(
        active: UUID? = Constants.adminOrg,
        switchFails: Bool = false,
        leaveError: Error? = nil,
        members: [OrgMemberResponse]? = nil,
        membersError: Error? = nil
    ) -> SUT {
        let memberships = Self.bothOrganisations()

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user(active: active, memberships: memberships))
        if switchFails {
            given(session).setActiveOrganisation(.any).willThrow(TestError.unreachable)
        } else {
            given(session).setActiveOrganisation(.any).willReturn(
                Self.user(active: Constants.memberOrg, memberships: memberships)
            )
        }
        given(session).refresh().willReturn(Self.user(active: nil, memberships: []))

        let organisations = MockOrganisationService(policy: .relaxed)
        if let leaveError {
            given(organisations).leave(orgId: .any).willThrow(leaveError)
        } else {
            given(organisations).leave(orgId: .any).willReturn(())
        }
        given(organisations).removeMember(orgId: .any, userId: .any).willReturn(())
        if let membersError {
            given(organisations).members(orgId: .any).willThrow(membersError)
        } else {
            given(organisations).members(orgId: .any).willReturn(
                members ?? [Self.member(role: .orgAdmin, id: Constants.userId),
                            Self.member(role: .orgMember, id: UUID())]
            )
        }

        Container.shared.sessionService.register { session }
        Container.shared.organisationService.register { organisations }
        return SUT(model: SettingsViewModel(), session: session, organisations: organisations)
    }

    // MARK: Administrative capability

    @Test("an administrator of the active organisation sees admin capability")
    func adminOfActiveOrganisation() async {
        let sut = makeSUT(active: Constants.adminOrg)

        await sut.model.load()

        #expect(sut.model.isOrgAdmin)
    }

    @Test("being an administrator elsewhere does not grant it here")
    func adminElsewhereDoesNotCount() async {
        // The bug this prevents: showing admin controls to somebody who administers a different
        // clinic, which the server would then refuse.
        let sut = makeSUT(active: Constants.memberOrg)

        await sut.model.load()

        #expect(sut.model.isOrgAdmin == false)
    }

    @Test("no active organisation means no admin capability")
    func noActiveOrganisation() async {
        let sut = makeSUT(active: nil)

        await sut.model.load()

        #expect(sut.model.isOrgAdmin == false)
    }

    // MARK: Switching

    @Test("switching changes the active organisation")
    func switchingChangesActive() async {
        let sut = makeSUT()
        await sut.model.load()

        await sut.model.switchTo(Constants.memberOrg)

        #expect(sut.model.activeOrganisation?.orgId == Constants.memberOrg)
        verify(sut.session).setActiveOrganisation(.value(Constants.memberOrg)).called(1)
    }

    @Test("switching signals the shell to reset, since what is on screen belongs elsewhere")
    func switchingSignalsReset() async {
        let sut = makeSUT()
        await sut.model.load()

        await sut.model.switchTo(Constants.memberOrg)

        #expect(sut.model.didSwitch)
    }

    @Test("switching to the one already active does nothing")
    func switchingToSameIsNoop() async {
        let sut = makeSUT()
        await sut.model.load()

        await sut.model.switchTo(Constants.adminOrg)

        verify(sut.session).setActiveOrganisation(.any).called(0)
    }

    @Test("a failed switch keeps the previous organisation and says so")
    func failedSwitch() async {
        let sut = makeSUT(switchFails: true)
        await sut.model.load()

        await sut.model.switchTo(Constants.memberOrg)

        #expect(sut.model.activeOrganisation?.orgId == Constants.adminOrg)
        #expect(sut.model.errorMessage != nil)
        #expect(sut.model.didSwitch == false)
    }

    // MARK: Leaving

    @Test("leaving calls leave, never remove-member, which would act on a colleague")
    func leaveUsesItsOwnEndpoint() async {
        let sut = makeSUT()
        await sut.model.load()

        await sut.model.leave()

        verify(sut.organisations).leave(orgId: .any).called(1)
        verify(sut.organisations).removeMember(orgId: .any, userId: .any).called(0)
        #expect(sut.model.didLeave)
    }

    @Test("the only administrator is warned the organisation will be archived, before leaving")
    func soleAdministratorIsWarnedTheOrganisationGoes() async {
        // The app used to say leaving "would strand this organisation" and to promote somebody
        // first. The server archives it instead, deliberately, and said so all along.
        let sut = makeSUT(members: [Self.member(role: .orgAdmin, id: Constants.userId)])

        await sut.model.load()

        #expect(sut.model.willArchiveOnLeave)
    }

    @Test("an administrator with a colleague to hand over to is not warned about archiving")
    func administratorWithAnotherAdministrator() async {
        let sut = makeSUT(
            members: [
                Self.member(role: .orgAdmin, id: Constants.userId),
                Self.member(role: .orgAdmin, id: UUID())
            ]
        )

        await sut.model.load()

        #expect(sut.model.willArchiveOnLeave == false)
    }

    @Test("an ordinary member is never warned about archiving, and the list is never fetched")
    func ordinaryMemberIsNotWarned() async {
        let sut = makeSUT(active: Constants.memberOrg)

        await sut.model.load()

        #expect(sut.model.willArchiveOnLeave == false)
        verify(sut.organisations).members(orgId: .any).called(0)
    }

    @Test("a member list that cannot be read leaves the ordinary warning in place")
    func unreadableMemberListFallsBack() async {
        let sut = makeSUT(membersError: TestError.unreachable)

        await sut.model.load()

        #expect(sut.model.willArchiveOnLeave == false)
        #expect(sut.model.errorMessage == nil)
    }

    @Test("any other failure gets the ordinary message rather than the sole-admin one")
    func otherLeaveFailure() async {
        let sut = makeSUT(leaveError: TestError.unreachable)
        await sut.model.load()

        await sut.model.leave()

        #expect(sut.model.didLeave == false)
        #expect(sut.model.errorMessage?.contains("administrator") == false)
    }
}
