import Factory
import Mockable
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite("SignUpViewModel", .serialized)
@MainActor
final class SignUpViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let email = "clinician@example.com"
        static let password = "Sup3rSecretPass"
    }

    private enum TestError: Error {
        case alreadyExists
    }

    private func makeSUT(auth: MockAuthService) -> SignUpViewModel {
        Container.shared.authService.register { auth }
        return SignUpViewModel()
    }

    @Test("refuses mismatched passwords without calling Cognito")
    func refusesMismatch() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = Constants.password
        model.confirmPassword = "Different1234"

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(model.didSignUp == nil)
        verify(auth).signUp(email: .any, password: .any).called(0)
    }

    @Test("refuses a password the pool would reject, without the round trip")
    func refusesWeakPassword() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = "short"
        model.confirmPassword = "short"

        await model.submit()

        #expect(model.errorMessage != nil)
        verify(auth).signUp(email: .any, password: .any).called(0)
    }

    @Test("refuses a missing email without calling Cognito")
    func refusesMissingEmail() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)
        model.password = Constants.password
        model.confirmPassword = Constants.password

        await model.submit()

        #expect(model.errorMessage != nil)
        verify(auth).signUp(email: .any, password: .any).called(0)
    }

    @Test("carries the address forward so the code is entered against the right account")
    func carriesEmailForward() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signUp(email: .any, password: .any).willReturn(())
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = Constants.password
        model.confirmPassword = Constants.password

        await model.submit()

        #expect(model.didSignUp == Constants.email)
        #expect(model.errorMessage == nil)
    }

    @Test("an already-registered address surfaces a message rather than advancing")
    func surfacesFailure() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).signUp(email: .any, password: .any).willThrow(TestError.alreadyExists)
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        model.password = Constants.password
        model.confirmPassword = Constants.password

        await model.submit()

        #expect(model.didSignUp == nil)
        #expect(model.errorMessage != nil)
        #expect(model.isBusy == false)
    }
}
