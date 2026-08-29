import Factory
import Foundation
import SimplicityFoundation

@Observable
@MainActor
public final class SignUpViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth

    // MARK: Properties

    public var email: String = .empty
    public var password: String = .empty
    public var confirmPassword: String = .empty
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    /// Carries the address forward, because the confirmation screen needs it and asking the person
    /// to type it again is how codes get entered against the wrong account.
    public private(set) var didSignUp: String?

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func submit() async {
        guard let message = validate() else {
            await create()
            return
        }
        errorMessage = message
    }

    // MARK: Private

    /// Returns the reason to refuse, or nil to proceed.
    private func validate() -> String? {
        if email.trimmingCharacters(in: .whitespaces).isEmpty {
            return String(localized: "sign_up_missing_email", bundle: .module)
        }
        if password != confirmPassword {
            return String(localized: "sign_up_passwords_differ", bundle: .module)
        }
        return PasswordPolicy.validate(password)
    }

    private func create() async {
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await auth.signUp(email: email, password: password)
            didSignUp = email
        } catch {
            errorMessage = String(localized: "sign_up_failed", bundle: .module)
        }
    }
}
