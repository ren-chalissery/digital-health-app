import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite("ProfileWizardViewModel", .serialized)
@MainActor
final class ProfileWizardViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let role = "Clinical psychologist"
    }

    private enum TestError: Error {
        case refused
    }

    nonisolated private static func onboardedUser() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: UUID(),
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    /// `.relaxed` covers Void returns only, so a session reaching `refresh()` traps unless it is
    /// given an answer. Every test that gets past validation reaches it.
    private func stubbedSession() -> MockSessionService {
        let session = MockSessionService(policy: .relaxed)
        given(session).refresh().willReturn(Self.onboardedUser())
        return session
    }

    private func makeSUT(
        session: MockSessionService? = nil,
        update: @escaping ProfileWizardViewModel.Update
    ) -> ProfileWizardViewModel {
        let resolved = session ?? stubbedSession()
        Container.shared.sessionService.register { resolved }
        return ProfileWizardViewModel(update: update)
    }

    @Test("refuses a blank name without a request")
    func refusesBlankName() async {
        let called = Counter()
        let model = makeSUT(update: { _ in
            await called.bump()
            return Self.onboardedUser()
        })
        model.professionalRole = Constants.role

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(model.didComplete == false)
        #expect(await called.value == 0)
    }

    @Test("refuses a missing professional role, which the web also requires")
    func refusesMissingRole() async {
        let called = Counter()
        let model = makeSUT(update: { _ in
            await called.bump()
            return Self.onboardedUser()
        })
        model.fullName = "A Clinician"

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(await called.value == 0)
    }

    @Test("sends no phone at all when the field is left empty")
    func omitsEmptyPhone() async throws {
        let captured = Captured()
        let model = makeSUT(update: { request in
            await captured.set(request)
            return Self.onboardedUser()
        })
        model.fullName = "A Clinician"
        model.professionalRole = Constants.role

        await model.submit()

        #expect(await captured.value?.phone == nil)
        #expect(await captured.value?.fullName == "A Clinician")
    }

    @Test("refreshes the session, so the shell sees profileCompleted and stops sending them back")
    func refreshesSession() async {
        let session = MockSessionService(policy: .relaxed)
        given(session).refresh().willReturn(Self.onboardedUser())
        let model = makeSUT(session: session, update: { _ in Self.onboardedUser() })
        model.fullName = "A Clinician"
        model.professionalRole = Constants.role

        await model.submit()

        #expect(model.didComplete)
        verify(session).refresh().called(1)
    }

    @Test("a failed update leaves the wizard in place with a message")
    func failedUpdate() async {
        let model = makeSUT(update: { _ in throw TestError.refused })
        model.fullName = "A Clinician"
        model.professionalRole = Constants.role

        await model.submit()

        #expect(model.didComplete == false)
        #expect(model.errorMessage != nil)
        #expect(model.isBusy == false)
    }

    private actor Counter {
        private(set) var value = 0
        func bump() { value += 1 }
    }

    private actor Captured {
        private(set) var value: UpdateProfileRequest?
        func set(_ request: UpdateProfileRequest) { value = request }
    }
}

@Suite("OrganisationWizardViewModel", .serialized)
@MainActor
final class OrganisationWizardViewModelTests: SimplicityTestCase {

    private enum TestError: Error {
        case refused
    }

    nonisolated private static func organisation() -> OrganisationResponse {
        OrganisationResponse(id: UUID(), name: "A Clinic")
    }

    nonisolated private static func onboardedUser() -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: UUID(),
            id: UUID(),
            profileCompleted: true,
            status: .active
        )
    }

    private func stubbedSession() -> MockSessionService {
        let session = MockSessionService(policy: .relaxed)
        given(session).refresh().willReturn(Self.onboardedUser())
        return session
    }

    private func makeSUT(
        session: MockSessionService? = nil,
        create: @escaping OrganisationWizardViewModel.Create
    ) -> OrganisationWizardViewModel {
        let resolved = session ?? stubbedSession()
        Container.shared.sessionService.register { resolved }
        return OrganisationWizardViewModel(create: create)
    }

    @Test("refuses a blank name without a request")
    func refusesBlankName() async {
        let called = Counter()
        let model = makeSUT(create: { _ in
            await called.bump()
            return Self.organisation()
        })

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(await called.value == 0)
    }

    @Test("trims the name, so a stray space does not become part of it")
    func trimsName() async {
        let captured = Captured()
        let model = makeSUT(create: { request in
            await captured.set(request)
            return Self.organisation()
        })
        model.name = "  A Clinic  "

        await model.submit()

        #expect(await captured.value?.name == "A Clinic")
    }

    @Test("refreshes the session so the shell learns there is now an active organisation")
    func refreshesSession() async {
        let session = MockSessionService(policy: .relaxed)
        given(session).refresh().willReturn(Self.onboardedUser())
        let model = makeSUT(session: session, create: { _ in Self.organisation() })
        model.name = "A Clinic"

        await model.submit()

        #expect(model.didComplete)
        verify(session).refresh().called(1)
    }

    @Test("a failed creation leaves the wizard in place with a message")
    func failedCreate() async {
        let model = makeSUT(create: { _ in throw TestError.refused })
        model.name = "A Clinic"

        await model.submit()

        #expect(model.didComplete == false)
        #expect(model.errorMessage != nil)
    }

    private actor Counter {
        private(set) var value = 0
        func bump() { value += 1 }
    }

    private actor Captured {
        private(set) var value: CreateOrganisationRequest?
        func set(_ request: CreateOrganisationRequest) { value = request }
    }
}
