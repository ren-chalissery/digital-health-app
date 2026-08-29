import Foundation
import Mockable
import SimplicityApi

/// The app's model of who is signed in.
///
/// Every screen reads this rather than calling `/me` itself, which is what keeps the tab bar, the
/// organisation switcher and the onboarding guard from disagreeing about the same person.
@Mockable
public protocol SessionService: AnyObject, Sendable {
    var current: CurrentUserResponse? { get async }

    @discardableResult
    func refresh() async throws -> CurrentUserResponse

    @discardableResult
    func setActiveOrganisation(_ id: UUID) async throws -> CurrentUserResponse

    func clear() async
}

public extension CurrentUserResponse {

    /// Two gates, matching the web's onboarding guard: a professional profile, and an organisation
    /// to work in. Either missing means the wizard rather than the app.
    var needsOnboarding: Bool {
        profileCompleted != true || activeOrganisationId == nil
    }

    /// Nil when the active id names an organisation the user is no longer a member of, which
    /// happens when they are removed while signed in elsewhere.
    var activeOrganisation: OrganisationMembershipResponse? {
        guard let activeOrganisationId else { return nil }
        return organisations?.first { $0.orgId == activeOrganisationId }
    }
}
