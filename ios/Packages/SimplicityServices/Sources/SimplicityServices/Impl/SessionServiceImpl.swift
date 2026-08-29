import Foundation
import SimplicityApi

/// An actor because the cached user is read from every screen and written by sign-in, onboarding
/// and organisation switching.
public actor SessionServiceImpl: SessionService {

    // MARK: Types

    public typealias Fetch = @Sendable () async throws -> CurrentUserResponse
    public typealias SetActive = @Sendable (UUID) async throws -> CurrentUserResponse

    // MARK: Properties

    private var cached: CurrentUserResponse?
    private let fetch: Fetch
    private let setActive: SetActive

    public var current: CurrentUserResponse? { cached }

    // MARK: Init

    /// The two calls are injected as closures rather than behind a protocol because the generated
    /// API exposes them as class functions, which cannot be mocked. A closure is the smallest seam
    /// that makes this testable without wrapping the whole generated client.
    public init(
        fetch: @escaping Fetch = { try await CurrentUserAPI.getCurrentUser() },
        setActive: @escaping SetActive = { id in
            try await CurrentUserAPI.setActiveOrganisation(
                setActiveOrganisationRequest: SetActiveOrganisationRequest(organisationId: id)
            )
        }
    ) {
        self.fetch = fetch
        self.setActive = setActive
    }

    // MARK: Functions

    /// A throw leaves the previously cached user untouched. Half-clearing on a failed refresh
    /// would sign someone out because their train went into a tunnel.
    @discardableResult
    public func refresh() async throws -> CurrentUserResponse {
        let user = try await fetch()
        cached = user
        return user
    }

    @discardableResult
    public func setActiveOrganisation(_ id: UUID) async throws -> CurrentUserResponse {
        let user = try await setActive(id)
        cached = user
        return user
    }

    public func clear() async {
        cached = nil
    }
}
