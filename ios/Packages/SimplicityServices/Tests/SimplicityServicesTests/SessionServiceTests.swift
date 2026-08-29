import Foundation
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite("SessionService", .serialized)
final class SessionServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let email = "clinician@example.com"
    }

    private enum TestError: Error {
        case unreachable
    }

    private func user(
        profileCompleted: Bool = true,
        activeOrganisationId: UUID? = Constants.orgId,
        organisations: [OrganisationMembershipResponse] = []
    ) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: activeOrganisationId,
            email: Constants.email,
            fullName: "A Clinician",
            id: UUID(),
            organisations: organisations,
            platformRole: .standard,
            professionalRole: "Psychologist",
            profileCompleted: profileCompleted,
            status: .active
        )
    }

    // MARK: Onboarding

    @Test("needs onboarding when the profile is incomplete")
    func needsOnboardingWithoutProfile() {
        #expect(user(profileCompleted: false).needsOnboarding)
    }

    @Test("needs onboarding when the profile is done but there is no organisation")
    func needsOnboardingWithoutOrganisation() {
        #expect(user(activeOrganisationId: nil).needsOnboarding)
    }

    @Test("does not need onboarding with both a profile and an organisation")
    func onboardedUser() {
        #expect(user().needsOnboarding == false)
    }

    // MARK: Active organisation

    @Test("resolves the active organisation from the memberships list")
    func resolvesActiveOrganisation() {
        let membership = OrganisationMembershipResponse(name: "A Clinic", orgId: Constants.orgId)
        let subject = user(organisations: [membership])

        #expect(subject.activeOrganisation?.name == "A Clinic")
    }

    @Test("has no active organisation when the id names one the user is not a member of")
    func activeOrganisationMissingFromMemberships() {
        let other = OrganisationMembershipResponse(name: "Elsewhere", orgId: UUID())

        #expect(user(organisations: [other]).activeOrganisation == nil)
    }

    // MARK: Caching

    @Test("caches the user after a refresh, so callers do not each fetch")
    func cachesAfterRefresh() async throws {
        let service = SessionServiceImpl(fetch: { self.user() })

        #expect(await service.current == nil)
        _ = try await service.refresh()

        #expect(await service.current?.email == Constants.email)
    }

    @Test("clear forgets the user, so a sign-out cannot leak into the next session")
    func clearForgets() async throws {
        let service = SessionServiceImpl(fetch: { self.user() })
        _ = try await service.refresh()

        await service.clear()

        #expect(await service.current == nil)
    }

    @Test("a failed refresh leaves the previous user in place rather than half-clearing")
    func failedRefreshKeepsPreviousUser() async throws {
        let shouldFail = Failing()
        let service = SessionServiceImpl(fetch: {
            if await shouldFail.value { throw TestError.unreachable }
            return self.user()
        })
        _ = try await service.refresh()
        await shouldFail.set(true)

        await #expect(throws: TestError.self) { try await service.refresh() }
        #expect(await service.current?.email == Constants.email)
    }

    @Test("switching organisation replaces the cached user with the server's answer")
    func switchingOrganisationUpdatesCache() async throws {
        let replacement = UUID()
        let service = SessionServiceImpl(
            fetch: { self.user() },
            setActive: { id in self.user(activeOrganisationId: id) }
        )
        _ = try await service.refresh()

        _ = try await service.setActiveOrganisation(replacement)

        #expect(await service.current?.activeOrganisationId == replacement)
    }

    private actor Failing {
        private(set) var value = false
        func set(_ newValue: Bool) { value = newValue }
    }
}
