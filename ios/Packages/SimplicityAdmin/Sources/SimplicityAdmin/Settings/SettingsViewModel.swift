import Factory
import Foundation
import SimplicityApi
import SimplicityServices

@Observable
@MainActor
public final class SettingsViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.sessionService) private var session
    @ObservationIgnored @Injected(\.organisationService) private var organisations

    // MARK: Properties

    public private(set) var user: CurrentUserResponse?
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    /// Set when the active organisation changes, so the shell can reset its navigation. Everything
    /// already on screen belongs to the organisation being left.
    public private(set) var didSwitch = false

    /// Set when leaving succeeds, so the shell can send them back to onboarding or sign-out.
    public private(set) var didLeave = false

    /// True when this person is the only administrator, in which case leaving archives the
    /// organisation. The server does this deliberately rather than leaving it unadministered, so
    /// the confirmation has to say so before they commit, not afterwards.
    ///
    /// False when it cannot be determined, which leaves the ordinary warning in place: a wrong
    /// claim that nothing will happen is milder than a wrong claim that everything will.
    public private(set) var willArchiveOnLeave = false

    public var memberships: [OrganisationMembershipResponse] {
        user?.organisations ?? []
    }

    public var activeOrganisation: OrganisationMembershipResponse? {
        user?.activeOrganisation
    }

    /// Administrative capability comes from the **active** organisation, never from being an
    /// administrator somewhere else. Someone who administers one clinic and merely belongs to
    /// another must not see admin controls while the second is active.
    public var isOrgAdmin: Bool {
        activeOrganisation?.orgRole == .orgAdmin
    }

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func load() async {
        user = await session.current
        if user == nil {
            try? await session.refresh()
            user = await session.current
        }
        await determineWhetherLeavingArchives()
    }

    public func switchTo(_ orgId: UUID) async {
        guard orgId != user?.activeOrganisationId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        didSwitch = false
        defer { isBusy = false }

        do {
            user = try await session.setActiveOrganisation(orgId)
            didSwitch = true
        } catch {
            errorMessage = String(localized: "settings_switch_failed", bundle: .module)
        }
    }

    public func leave() async {
        guard let orgId = user?.activeOrganisationId, !isBusy else { return }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await organisations.leave(orgId: orgId)
            user = try await session.refresh()
            didLeave = true
        } catch {
            errorMessage = String(localized: "settings_leave_failed", bundle: .module)
        }
    }

    public func acknowledgeSwitch() {
        didSwitch = false
    }

    // MARK: Private

    /// Only an administrator may read the member list, and only an administrator can be the last
    /// one, so an ordinary member never provokes the request.
    private func determineWhetherLeavingArchives() async {
        willArchiveOnLeave = false

        guard isOrgAdmin, let orgId = user?.activeOrganisationId, let me = user?.id else { return }

        guard let members = try? await organisations.members(orgId: orgId) else {
            // Silent: this is a detail of a warning, not something they asked for, and an error
            // banner on opening Settings would be baffling.
            return
        }

        let administrators = members.filter { $0.orgRole == .orgAdmin }
        willArchiveOnLeave = administrators.count == 1 && administrators.first?.userId == me
    }
}
