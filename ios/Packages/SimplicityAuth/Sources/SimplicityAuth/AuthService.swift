import Foundation
import Mockable

/// The only place in the app that talks to Cognito.
///
/// Everything else deals in "signed in or not" and an access token, so swapping the identity
/// provider would not reach past this file — the same boundary the web draws in
/// `web/src/app/core/auth/auth.service.ts`.
@Mockable
public protocol AuthService: AnyObject, Sendable {

    func signUp(email: String, password: String) async throws
    func confirmSignUp(email: String, code: String) async throws
    func resendConfirmationCode(email: String) async throws

    /// Returns false when Cognito needs something more before a session exists — an unconfirmed
    /// address, or a challenge. The caller routes on that rather than treating it as an error.
    func signIn(email: String, password: String) async throws -> Bool

    func signOut() async
    func startPasswordReset(email: String) async throws
    func confirmPasswordReset(email: String, code: String, newPassword: String) async throws
    func isSignedIn() async -> Bool

    /// Nil rather than a throw when signed out: the caller is usually a request builder that
    /// should proceed unauthenticated, not fail.
    func accessToken() async -> String?
}
