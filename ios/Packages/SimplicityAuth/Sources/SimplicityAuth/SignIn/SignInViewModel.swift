import Factory
import Foundation
import SimplicityFoundation
import SimplicityServices

public enum SignInOutcome: Equatable {
    case signedIn
    case needsConfirmation
    case needsOnboarding
}

@Observable
@MainActor
public final class SignInViewModel {

    // MARK: Dependencies

    @ObservationIgnored @Injected(\.authService) private var auth
    @ObservationIgnored @Injected(\.sessionService) private var session

    // MARK: Properties

    public var email: String = .empty
    public var password: String = .empty
    public private(set) var isBusy = false
    public private(set) var errorMessage: String?

    /// Read by the shell's router. The screen takes no navigation decisions itself, which is what
    /// keeps this package independent of the app's routes.
    public private(set) var outcome: SignInOutcome?

    // MARK: Init

    public init() {}

    // MARK: Functions

    public func submit() async {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            errorMessage = String(localized: "sign_in_missing_fields", bundle: .module)
            return
        }

        isBusy = true
        errorMessage = nil
        defer { isBusy = false }

        do {
            guard try await auth.signIn(email: email, password: password) else {
                // Not a failure: Cognito wants the emailed code before there is a session.
                outcome = .needsConfirmation
                return
            }
            let user = try await session.refresh()
            outcome = user.needsOnboarding ? .needsOnboarding : .signedIn
        } catch {
            errorMessage = String(localized: "sign_in_failed", bundle: .module)
        }
    }
}
