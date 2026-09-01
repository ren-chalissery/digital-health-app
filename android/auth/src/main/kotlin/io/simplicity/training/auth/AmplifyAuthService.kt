package io.simplicity.training.auth

import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.kotlin.core.Amplify

/**
 * Cognito through Amplify, using SRP — the password never leaves the device.
 *
 * Every method is a thin translation. Anything resembling a decision belongs in a view model, so
 * that it can be tested without Amplify being configured.
 */
class AmplifyAuthService : AuthService {

    override suspend fun signUp(email: String, password: String) {
        Amplify.Auth.signUp(
            username = email,
            password = password,
            options = AuthSignUpOptions.builder()
                .userAttribute(AuthUserAttributeKey.email(), email)
                .build(),
        )
    }

    override suspend fun confirmSignUp(email: String, code: String) {
        Amplify.Auth.confirmSignUp(email, code)
    }

    override suspend fun resendConfirmationCode(email: String) {
        Amplify.Auth.resendSignUpCode(email)
    }

    override suspend fun signIn(email: String, password: String): Boolean {
        // Amplify refuses a second signIn while a session remains locally — common after an infra
        // pause when Cognito survived but the app signed the user out of our API only.
        if (isSignedIn()) {
            signOut()
        }
        val result = Amplify.Auth.signIn(email, password)
        // DONE means a session exists. Anything else — an unconfirmed address, a challenge — is a
        // routing decision for the caller, not a failure.
        return result.isSignedIn && result.nextStep.signInStep == AuthSignInStep.DONE
    }

    override suspend fun signOut() {
        Amplify.Auth.signOut()
    }

    override suspend fun startPasswordReset(email: String) {
        Amplify.Auth.resetPassword(email)
    }

    override suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        Amplify.Auth.confirmResetPassword(email, newPassword, code)
    }

    override suspend fun isSignedIn(): Boolean =
        runCatching { Amplify.Auth.fetchAuthSession().isSignedIn }.getOrDefault(false)

    override suspend fun accessToken(): String? = token(forceRefresh = false)

    override suspend fun refreshedAccessToken(): String? = token(forceRefresh = true)

    /**
     * Amplify refreshes on its own only when a token is close to expiry, which is why the forced
     * variant exists: the server can void a token long before then.
     */
    private suspend fun token(forceRefresh: Boolean): String? = runCatching {
        val session = if (forceRefresh) {
            Amplify.Auth.fetchAuthSession(
                com.amplifyframework.auth.options.AuthFetchSessionOptions.builder()
                    .forceRefresh(true)
                    .build(),
            )
        } else {
            Amplify.Auth.fetchAuthSession()
        }
        (session as? AWSCognitoAuthSession)?.userPoolTokensResult?.value?.accessToken
    }.getOrNull()
}
