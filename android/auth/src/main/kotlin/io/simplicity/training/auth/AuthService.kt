package io.simplicity.training.auth

/**
 * Everything the app needs from Cognito, and nothing about how it is reached.
 *
 * Mirrors `AuthService.swift` member for member, so the two clients cannot drift into offering
 * different capabilities. An interface rather than the Amplify types directly, so view models are
 * testable without a network or a configured Amplify.
 */
interface AuthService {

    suspend fun signUp(email: String, password: String)

    suspend fun confirmSignUp(email: String, code: String)

    suspend fun resendConfirmationCode(email: String)

    /**
     * False when Cognito needs something more before a session exists — an unconfirmed address, or
     * a challenge. The caller routes on that rather than treating it as an error.
     */
    suspend fun signIn(email: String, password: String): Boolean

    suspend fun signOut()

    suspend fun startPasswordReset(email: String)

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String)

    suspend fun isSignedIn(): Boolean

    /**
     * Null rather than throwing when signed out: the caller is usually a request builder that
     * should proceed unauthenticated, not fail.
     */
    suspend fun accessToken(): String?

    /**
     * A token obtained by forcing a refresh, for a request the server has just rejected.
     *
     * Distinct from [accessToken], which returns what Amplify holds and only refreshes near expiry.
     * The server voids a token long before then when somebody's access changes, and only insisting
     * on a new one recovers from it.
     */
    suspend fun refreshedAccessToken(): String?
}
