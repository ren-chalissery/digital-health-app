import Amplify
import AWSCognitoAuthPlugin
import AWSPluginsCore
import Foundation

/// Amplify rather than a hand-rolled SRP exchange: Cognito's is a password-authenticated key
/// agreement, and implementing it by hand means implementing the crypto by hand. This also matches
/// the Amplify JS flow the web already uses, so the two clients fail in the same ways.
public final class AmplifyAuthService: AuthService {

    // MARK: Init

    public init() {}

    /// Called once at launch, before anything resolves this service.
    public static func configure() throws {
        try Amplify.add(plugin: AWSCognitoAuthPlugin())
        try Amplify.configure()
    }

    // MARK: Functions

    public func signUp(email: String, password: String) async throws {
        let options = AuthSignUpRequest.Options(
            userAttributes: [AuthUserAttribute(.email, value: email)]
        )
        _ = try await Amplify.Auth.signUp(username: email, password: password, options: options)
    }

    public func confirmSignUp(email: String, code: String) async throws {
        _ = try await Amplify.Auth.confirmSignUp(for: email, confirmationCode: code)
    }

    public func resendConfirmationCode(email: String) async throws {
        _ = try await Amplify.Auth.resendSignUpCode(for: email)
    }

    public func signIn(email: String, password: String) async throws -> Bool {
        // Amplify refuses a second signIn while a session remains in the Keychain — common after
        // an infra pause when Cognito survived but the app signed the user out of our API only.
        if await isSignedIn() {
            await signOut()
        }
        return try await Amplify.Auth.signIn(username: email, password: password).isSignedIn
    }

    public func signOut() async {
        _ = await Amplify.Auth.signOut()
    }

    public func startPasswordReset(email: String) async throws {
        _ = try await Amplify.Auth.resetPassword(for: email)
    }

    public func confirmPasswordReset(email: String, code: String, newPassword: String) async throws {
        try await Amplify.Auth.confirmResetPassword(
            for: email,
            with: newPassword,
            confirmationCode: code
        )
    }

    public func isSignedIn() async -> Bool {
        (try? await Amplify.Auth.fetchAuthSession().isSignedIn) ?? false
    }

    /// Amplify refreshes the access token here when it is close to expiry, which is why every
    /// request asks for one rather than holding on to it.
    public func accessToken() async -> String? {
        guard
            let session = try? await Amplify.Auth.fetchAuthSession(),
            let provider = session as? AuthCognitoTokensProvider,
            let tokens = try? provider.getCognitoTokens().get()
        else {
            return nil
        }
        return tokens.accessToken
    }

    public func refreshedAccessToken() async -> String? {
        guard
            let session = try? await Amplify.Auth.fetchAuthSession(
                options: .forceRefresh()
            ),
            let provider = session as? AuthCognitoTokensProvider,
            let tokens = try? provider.getCognitoTokens().get()
        else {
            return nil
        }
        return tokens.accessToken
    }
}
