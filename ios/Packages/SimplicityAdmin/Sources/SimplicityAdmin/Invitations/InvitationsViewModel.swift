import Factory
import Foundation
import SimplicityApi
import SimplicityFoundation
import SimplicityServices

@Observable
@MainActor
public final class InvitationsViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.organisationService) private var organisations
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public private(set) var invitations: [InvitationResponse] = []
    public private(set) var teams: [TeamResponse] = []
    public private(set) var isLoading = false
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    public var email: String = .empty
    public var orgRole: CreateInvitationRequest.OrgRole = .orgMember
    public var teamId: UUID?
    public var teamRole: CreateInvitationRequest.TeamRole = .teamMember

    /// Client-side email validation is usually not worth doing, but an invitation to a malformed
    /// address fails silently from the sender's point of view: they see it sent and nobody arrives.
    public var canInvite: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.contains("@")
            && !trimmed.hasPrefix("@")
            && !trimmed.hasSuffix("@")
            && !isBusy
    }

    private var orgId: UUID?

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func load() async {
        orgId = await session.current?.activeOrganisationId
        guard let orgId else {
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            invitations = try await organisations.invitations(orgId: orgId)
            teams = try await organisations.teams(orgId: orgId)
        } catch {
            errorMessage = String(localized: "invitations_load_failed", bundle: .module)
        }
    }

    public func invite() async {
        guard canInvite, let orgId else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            let created = try await organisations.invite(
                orgId: orgId,
                email: email,
                orgRole: orgRole,
                teamId: teamId,
                teamRole: teamRole
            )
            invitations.append(created)
            email = .empty
            teamId = nil
        } catch {
            // The address stays in the field so it does not have to be retyped.
            errorMessage = String(localized: "invitations_send_failed", bundle: .module)
        }
    }

    public func revoke(_ invitation: InvitationResponse) async {
        guard let orgId, let id = invitation.id, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.revokeInvitation(orgId: orgId, invitationId: id)
            invitations.removeAll { $0.id == id }
        } catch {
            errorMessage = String(localized: "invitations_revoke_failed", bundle: .module)
        }
    }
}

public extension InvitationResponse {

    /// Only a pending invitation can be revoked. One already accepted is a membership now, and
    /// removing that person is a different action in a different place.
    var canRevoke: Bool {
        status == .pending
    }
}
