import Foundation
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite("OrganisationService", .serialized)
final class OrganisationServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let teamId = UUID()
    }

    private enum TestError: Error {
        case refused
    }

    nonisolated private static func team() -> TeamResponse {
        TeamResponse(id: Constants.teamId, name: "A team")
    }

    nonisolated private static func invitation() -> InvitationResponse {
        InvitationResponse(email: "ana@example.com", id: UUID(), status: .pending)
    }

    // MARK: Teams

    @Test("an empty description is sent as no description, not as an empty string")
    func emptyDescriptionBecomesNil() async throws {
        let captured = CapturedTeam()
        let service = OrganisationServiceImpl(createTeam: { _, request in
            await captured.set(request)
            return Self.team()
        })

        _ = try await service.createTeam(orgId: Constants.orgId, name: "Ward", description: "   ")

        #expect(await captured.value?.description == nil)
    }

    @Test("a team name is trimmed")
    func trimsTeamName() async throws {
        let captured = CapturedTeam()
        let service = OrganisationServiceImpl(createTeam: { _, request in
            await captured.set(request)
            return Self.team()
        })

        _ = try await service.createTeam(orgId: Constants.orgId, name: "  Ward  ", description: nil)

        #expect(await captured.value?.name == "Ward")
    }

    // MARK: Invitations

    @Test("an address is normalised, so a stray capital reaches the same person")
    func normalisesEmail() async throws {
        let captured = CapturedInvite()
        let service = OrganisationServiceImpl(invite: { _, request in
            await captured.set(request)
            return Self.invitation()
        })

        _ = try await service.invite(
            orgId: Constants.orgId,
            email: "  Ana@Example.com ",
            orgRole: .orgMember,
            teamId: nil,
            teamRole: nil
        )

        #expect(await captured.value?.email == "ana@example.com")
    }

    @Test("choosing no team sends no team role, which the server would otherwise refuse")
    func dropsDanglingTeamRole() async throws {
        let captured = CapturedInvite()
        let service = OrganisationServiceImpl(invite: { _, request in
            await captured.set(request)
            return Self.invitation()
        })

        _ = try await service.invite(
            orgId: Constants.orgId,
            email: "ana@example.com",
            orgRole: .orgMember,
            teamId: nil,
            teamRole: .teamMember
        )

        #expect(await captured.value?.teamId == nil)
        #expect(await captured.value?.teamRole == nil)
    }

    @Test("choosing a team sends both the team and the role")
    func sendsTeamAndRole() async throws {
        let captured = CapturedInvite()
        let service = OrganisationServiceImpl(invite: { _, request in
            await captured.set(request)
            return Self.invitation()
        })

        _ = try await service.invite(
            orgId: Constants.orgId,
            email: "ana@example.com",
            orgRole: .orgMember,
            teamId: Constants.teamId,
            teamRole: .teamAdmin
        )

        #expect(await captured.value?.teamId == Constants.teamId)
        #expect(await captured.value?.teamRole == .teamAdmin)
    }

    // MARK: Leaving

    @Test("leaving calls leave, never remove-member, which would act on somebody else")
    func leaveUsesItsOwnEndpoint() async throws {
        let leaveCalled = Flag()
        let removeCalled = Flag()
        let service = OrganisationServiceImpl(
            removeMember: { _, _ in await removeCalled.raise() },
            leave: { _ in await leaveCalled.raise() }
        )

        try await service.leave(orgId: Constants.orgId)

        #expect(await leaveCalled.value)
        #expect(await removeCalled.value == false)
    }

    @Test("a refusal propagates, so the caller can explain why")
    func propagatesRefusal() async {
        let service = OrganisationServiceImpl(leave: { _ in throw TestError.refused })

        await #expect(throws: TestError.self) { try await service.leave(orgId: Constants.orgId) }
    }

    private actor CapturedTeam {
        private(set) var value: CreateTeamRequest?
        func set(_ request: CreateTeamRequest) { value = request }
    }

    private actor CapturedInvite {
        private(set) var value: CreateInvitationRequest?
        func set(_ request: CreateInvitationRequest) { value = request }
    }

    private actor Flag {
        private(set) var value = false
        func raise() { value = true }
    }
}
