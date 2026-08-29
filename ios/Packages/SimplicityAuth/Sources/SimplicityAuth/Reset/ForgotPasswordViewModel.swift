import Factory
import Foundation
import SimplicityFoundation

public enum ResetStage: Equatable {
    case requestingCode
    case enteringNewPassword
    case done
}

@Observable
@MainActor
public final class ForgotPasswordViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth

    // MARK: Properties

    public var email: String = .empty
    public var code: String = .empty
    public var newPassword: String = .empty
    public private(set) var stage: ResetStage = .requestingCode
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func submit() async {
        switch stage {
        case .requestingCode: await requestCode()
        case .enteringNewPassword: await setNewPassword()
        case .done: break
        }
    }

    // MARK: Private

    private func requestCode() async {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = String(localized: "reset_missing_email", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await auth.startPasswordReset(email: email)
            stage = .enteringNewPassword
        } catch {
            // Deliberately vague, and the stage does not advance on failure — but note that
            // Cognito does not distinguish an unknown address here either, by design.
            errorMessage = String(localized: "reset_request_failed", bundle: .module)
        }
    }

    private func setNewPassword() async {
        if code.trimmingCharacters(in: .whitespaces).isEmpty {
            errorMessage = String(localized: "reset_missing_code", bundle: .module)
            return
        }
        if let message = PasswordPolicy.validate(newPassword) {
            errorMessage = message
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            try await auth.confirmPasswordReset(
                email: email,
                code: code,
                newPassword: newPassword
            )
            stage = .done
        } catch {
            errorMessage = String(localized: "reset_confirm_failed", bundle: .module)
        }
    }
}
