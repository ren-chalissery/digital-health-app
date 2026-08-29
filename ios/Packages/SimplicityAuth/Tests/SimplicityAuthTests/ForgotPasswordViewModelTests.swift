import Factory
import Mockable
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite("ForgotPasswordViewModel", .serialized)
@MainActor
final class ForgotPasswordViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let email = "clinician@example.com"
        static let password = "Sup3rSecretPass"
    }

    private enum TestError: Error {
        case refused
    }

    private func makeSUT(auth: MockAuthService) -> ForgotPasswordViewModel {
        Container.shared.authService.register { auth }
        return ForgotPasswordViewModel()
    }

    @Test("starts by asking for a code")
    func startsAtRequestingCode() {
        #expect(makeSUT(auth: MockAuthService(policy: .relaxed)).stage == .requestingCode)
    }

    @Test("advances to the new password only after the code has been sent")
    func advancesAfterCodeSent() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).startPasswordReset(email: .any).willReturn(())
        let model = makeSUT(auth: auth)
        model.email = Constants.email

        await model.submit()

        #expect(model.stage == .enteringNewPassword)
    }

    @Test("stays put when the code could not be sent")
    func staysWhenRequestFails() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).startPasswordReset(email: .any).willThrow(TestError.refused)
        let model = makeSUT(auth: auth)
        model.email = Constants.email

        await model.submit()

        #expect(model.stage == .requestingCode)
        #expect(model.errorMessage != nil)
    }

    @Test("refuses a new password the pool would reject, without the round trip")
    func refusesWeakNewPassword() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).startPasswordReset(email: .any).willReturn(())
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        await model.submit()
        model.code = "123456"
        model.newPassword = "short"

        await model.submit()

        #expect(model.stage == .enteringNewPassword)
        #expect(model.errorMessage != nil)
        verify(auth).confirmPasswordReset(email: .any, code: .any, newPassword: .any).called(0)
    }

    @Test("completes when the code and a valid password are accepted")
    func completes() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).startPasswordReset(email: .any).willReturn(())
        given(auth).confirmPasswordReset(email: .any, code: .any, newPassword: .any).willReturn(())
        let model = makeSUT(auth: auth)
        model.email = Constants.email
        await model.submit()
        model.code = "123456"
        model.newPassword = Constants.password

        await model.submit()

        #expect(model.stage == .done)
        #expect(model.errorMessage == nil)
    }
}
