import Factory
import Foundation
import SimplicityFoundation

@Observable
@MainActor
public final class ConfirmEmailViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth

    // MARK: Properties

    public var email: String
    public var code: String = .empty
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?
    public private(set) var didConfirm = false

    /// Separate from `didConfirm`, because resending is a success the person needs told about
    /// without the screen advancing.
    public private(set) var didResend = false

    // MARK: Init

    public init(email: String) {
        self.email = email
    }

    // MARK: Functions

    public func submit() async {
        guard !code.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = String(localized: "confirm_missing_code", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        didResend = false
        defer { isBusy = false }

        do {
            try await auth.confirmSignUp(email: email, code: code)
            didConfirm = true
        } catch {
            errorMessage = String(localized: "confirm_failed", bundle: .module)
        }
    }

    public func resend() async {
        isBusy = true
        errorMessage = nil
        didResend = false
        defer { isBusy = false }

        do {
            try await auth.resendConfirmationCode(email: email)
            didResend = true
        } catch {
            errorMessage = String(localized: "confirm_resend_failed", bundle: .module)
        }
    }
}
