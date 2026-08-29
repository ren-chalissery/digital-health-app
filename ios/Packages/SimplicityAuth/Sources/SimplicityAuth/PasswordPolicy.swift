import Foundation

/// The Cognito pool's own rule, checked before the round trip.
///
/// Duplicating a server-side rule on the client is usually a mistake, but a rejected password is
/// the one case where the round trip tells the person nothing they could not have been told
/// immediately — and Cognito's own message names the policy rather than what they typed.
public enum PasswordPolicy {

    private enum Constants {
        static let minimumLength = 12
    }

    /// Nil when acceptable, otherwise the reason.
    public static func validate(_ password: String) -> String? {
        guard password.count >= Constants.minimumLength else {
            return String(localized: "password_too_short", bundle: .module)
        }
        guard password.contains(where: \.isUppercase) else {
            return String(localized: "password_needs_uppercase", bundle: .module)
        }
        guard password.contains(where: \.isLowercase) else {
            return String(localized: "password_needs_lowercase", bundle: .module)
        }
        guard password.contains(where: \.isNumber) else {
            return String(localized: "password_needs_number", bundle: .module)
        }
        return nil
    }
}
