import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAdmin

@Suite("InvitationsViewModel", .serialized)
@MainActor
final class InvitationsViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let teamId = UUID()
    }

    private enum TestError: Error {
        case unreachable
    }

    nonisolated private static func invitation(
        status: InvitationResponse.Status = .pending
    ) -> InvitationResponse {
        InvitationResponse(email: "ana@example.com", id: UUID(), status: status)
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
        invitations: [InvitationResponse] = [],
        inviteFails: Bool = false
    ) -> (InvitationsViewModel, MockOrganisationService) {
        let organisations = MockOrganisationService(policy: .relaxed)
        given(organisations).invitations(orgId: .any).willReturn(invitations)
        given(organisations).teams(orgId: .any).willReturn([
            TeamResponse(id: Constants.teamId, name: "Ward")
        ])
        if inviteFails {
            given(organisations)
                .invite(orgId: .any, email: .any, orgRole: .any, teamId: .any, teamRole: .any)
                .willThrow(TestError.unreachable)
        } else {
            given(organisations)
                .invite(orgId: .any, email: .any, orgRole: .any, teamId: .any, teamRole: .any)
                .willReturn(Self.invitation())
        }
        given(organisations).revokeInvitation(orgId: .any, invitationId: .any).willReturn(())

        let session = MockSessionService(policy: .relaxed)
        given(session).current.willReturn(Self.user())

        Container.shared.organisationService.register { organisations }
        Container.shared.sessionService.register { session }
        return (InvitationsViewModel(), organisations)
    }

    // MARK: Validation

    @Test("an address with no at-sign cannot be invited")
    func rejectsMalformedAddress() async {
        let (model, organisations) = makeSUT()
        await model.load()
        model.email = "not an address"

        await model.invite()

        #expect(model.canInvite == false)
        verify(organisations)
            .invite(orgId: .any, email: .any, orgRole: .any, teamId: .any, teamRole: .any)
            .called(0)
    }

    @Test("an empty address cannot be invited")
    func rejectsEmptyAddress() async {
        let (model, _) = makeSUT()
        await model.load()

        #expect(model.canInvite == false)
    }

    @Test("a plausible address can be invited")
    func acceptsPlausibleAddress() async {
        let (model, _) = makeSUT()
        await model.load()
        model.email = "ana@example.com"

        #expect(model.canInvite)
    }

    // MARK: Inviting

    @Test("inviting sends the chosen organisation role")
    func sendsOrgRole() async {
        let (model, organisations) = makeSUT()
        await model.load()
        model.email = "ana@example.com"
        model.orgRole = .orgAdmin

        await model.invite()

        verify(organisations)
            .invite(orgId: .any, email: .any, orgRole: .value(.orgAdmin),
                    teamId: .any, teamRole: .any)
            .called(1)
    }

    @Test("inviting into a team sends the team as well")
    func sendsTeam() async {
        let (model, organisations) = makeSUT()
        await model.load()
        model.email = "ana@example.com"
        model.teamId = Constants.teamId

        await model.invite()

        verify(organisations)
            .invite(orgId: .any, email: .any, orgRole: .any,
                    teamId: .value(Constants.teamId), teamRole: .any)
            .called(1)
    }

    @Test("a successful invitation is listed and the address cleared")
    func successListsAndClears() async {
        let (model, _) = makeSUT()
        await model.load()
        model.email = "ana@example.com"

        await model.invite()

        #expect(model.invitations.count == 1)
        #expect(model.email.isEmpty)
    }

    @Test("a failed invitation keeps the address so it need not be retyped")
    func failureKeepsAddress() async {
        let (model, _) = makeSUT(inviteFails: true)
        await model.load()
        model.email = "ana@example.com"

        await model.invite()

        #expect(model.email == "ana@example.com")
        #expect(model.errorMessage != nil)
    }

    // MARK: Revoking

    @Test("revoking removes it from the list")
    func revokeRemoves() async {
        let pending = Self.invitation()
        let (model, _) = makeSUT(invitations: [pending])
        await model.load()

        await model.revoke(pending)

        #expect(model.invitations.isEmpty)
    }

    @Test("an accepted invitation cannot be revoked — that person is a member now")
    func acceptedCannotBeRevoked() {
        #expect(Self.invitation(status: .accepted).canRevoke == false)
        #expect(Self.invitation(status: .revoked).canRevoke == false)
        #expect(Self.invitation(status: .expired).canRevoke == false)
        #expect(Self.invitation(status: .pending).canRevoke)
    }
}
