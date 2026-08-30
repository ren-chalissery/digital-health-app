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
            // Currently unreachable: production returns 204 even for the only administrator, so a
            // sole admin can strand their own organisation. That looks like a server gap rather
            // than a decision, and the branch stays so the app explains it usefully the day the
            // guard appears — but nothing here should be read as evidence that it exists.
            errorMessage = Self.isConflict(error)
                ? String(localized: "settings_leave_last_admin", bundle: .module)
                : String(localized: "settings_leave_failed", bundle: .module)
        }
    }

    public func acknowledgeSwitch() {
        didSwitch = false
    }

    // MARK: Private

    /// Matches on the status code rather than the message: the generated client surfaces the
    /// former reliably and the latter not at all.
    private static func isConflict(_ error: Error) -> Bool {
        guard case let ErrorResponse.error(statusCode, _, _, _) = error else { return false }
        return statusCode == 409
    }
}
