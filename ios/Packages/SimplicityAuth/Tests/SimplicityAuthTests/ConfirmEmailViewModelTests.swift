import Factory
import Mockable
import SimplicityTesting
import Testing

@testable import SimplicityAuth

@Suite("ConfirmEmailViewModel", .serialized)
@MainActor
final class ConfirmEmailViewModelTests: SimplicityTestCase {

    private enum Constants {
        static let email = "clinician@example.com"
    }

    private enum TestError: Error {
        case wrongCode
    }

    private func makeSUT(auth: MockAuthService) -> ConfirmEmailViewModel {
        Container.shared.authService.register { auth }
        return ConfirmEmailViewModel(email: Constants.email)
    }

    @Test("refuses an empty code without calling Cognito")
    func refusesEmptyCode() async {
        let auth = MockAuthService(policy: .relaxed)
        let model = makeSUT(auth: auth)

        await model.submit()

        #expect(model.errorMessage != nil)
        #expect(model.didConfirm == false)
        verify(auth).confirmSignUp(email: .any, code: .any).called(0)
    }

    @Test("a correct code confirms the account")
    func confirms() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).confirmSignUp(email: .any, code: .any).willReturn(())
        let model = makeSUT(auth: auth)
        model.code = "123456"

        await model.submit()

        #expect(model.didConfirm)
        #expect(model.errorMessage == nil)
    }

    @Test("a wrong code leaves the screen where it is")
    func surfacesWrongCode() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).confirmSignUp(email: .any, code: .any).willThrow(TestError.wrongCode)
        let model = makeSUT(auth: auth)
        model.code = "000000"

        await model.submit()

        #expect(model.didConfirm == false)
        #expect(model.errorMessage != nil)
    }

    @Test("resending reports success separately, so the screen does not advance")
    func resendIsItsOwnOutcome() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).resendConfirmationCode(email: .any).willReturn(())
        let model = makeSUT(auth: auth)

        await model.resend()

        #expect(model.didResend)
        #expect(model.didConfirm == false)
        #expect(model.errorMessage == nil)
    }

    @Test("a failed resend surfaces a message and does not claim success")
    func failedResend() async {
        let auth = MockAuthService(policy: .relaxed)
        given(auth).resendConfirmationCode(email: .any).willThrow(TestError.wrongCode)
        let model = makeSUT(auth: auth)

        await model.resend()

        #expect(model.didResend == false)
        #expect(model.errorMessage != nil)
    }
}
