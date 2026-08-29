import Factory
import Foundation
import Mockable
import SimplicityApi
import SimplicityServices
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite("SignInViewModel", .serialized)
@MainActor
final class SignInViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let email = "clinician@example.com"
        static let password = "Sup3rSecretPass"
    }

    private enum TestError: Error {
        case incorrect
    }

    private func user(
        profileCompleted: Bool = true,
        activeOrganisationId: UUID? = UUID()
    ) -> CurrentUserResponse {
        CurrentUserResponse(
            activeOrganisationId: activeOrganisationId,
            email: Constants.email,
            id: UUID(),
            organisations: [],
            platformRole: .standard,
            profileCompleted: profileCompleted,
            status: .active
        )
    }

    private func makeSUT(
        auth: MockAuthService = MockAuthService(policy: .relaxed),
        session: MockSessionService = MockSessionService(policy: .relaxed)
    ) -> SignInViewModel {
        Container.shared.authService.register { auth }
        Container.shared.sessionService.register { session }
        return SignInViewModel()
    }

    @Test("refuses to submit an empty form without calling Cognito")
    func refusesEmptyForm() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)

        await model.submit()

        #expect(model.errorMessage != nil)
        verify(auth).signIn(email: .any, password: .any).called(0)
    }

    @Test("refuses a whitespace-only email, which would otherwise reach Cognito")
    func refusesWhitespaceEmail() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)
        model.email = "   "
        model.password = Constants.password

        await model.submit()

        #expect(model.errorMessage != nil)
        verify(auth).signIn(email: .any, password: .any).called(0)
    }

    @Test("an onboarded user is signed in")
    func signsInOnboardedUser() async {
        let auth = MockAuthService(policy: .relaxed)
        let session = MockSessionService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(true)
        given(session).refresh().willReturn(user())
        let model = makeSUT(auth: auth, session: session)
        model.email = Constants.email
        model.password = Constants.password

        await model.submit()

        #expect(model.outcome == .signedIn)
        #expect(model.errorMessage == nil)
        #expect(model.isBusy == false)
    }

    @Test("an unconfirmed account is routed to confirmation, not shown an error")
    func routesUnconfirmedAccount() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(false)
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = Constants.password

        await model.submit()

        #expect(model.outcome == .needsConfirmation)
        #expect(model.errorMessage == nil)
    }

    @Test("a user without a profile is routed to onboarding")
    func routesUserNeedingOnboarding() async {
        let auth = MockAuthService(policy: .relaxed)
        let session = MockSessionService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(true)
        given(session).refresh().willReturn(
            user(profileCompleted: false, activeOrganisationId: nil)
        )
        let model = makeSUT(auth: auth, session: session)
        model.email = Constants.email
        model.password = Constants.password

        await model.submit()

        #expect(model.outcome == .needsOnboarding)
    }

    @Test("wrong credentials surface a message and leave the form usable")
    func surfacesFailure() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willThrow(TestError.incorrect)
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = "wrong"

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(model.isBusy == false)
        #expect(model.outcome == nil)
    }

    @Test("a failure after a valid password still clears busy, so the button is not stuck")
    func clearsBusyWhenSessionRefreshFails() async {
        let auth = MockAuthService(policy: .relaxed)
        let session = MockSessionService(policy: .relaxed)
        given(auth).signIn(email: .any, password: .any).willReturn(true)
        given(session).refresh().willThrow(TestError.incorrect)
        let model = makeSUT(auth: auth, session: session)
        model.email = Constants.email
        model.password = Constants.password

        await model.submit()

        #expect(model.isBusy == false)
        #expect(model.errorMessage != nil)
    }
}
