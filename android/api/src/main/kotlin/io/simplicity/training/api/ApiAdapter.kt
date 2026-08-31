package io.simplicity.training.api

/**
 * What `:api` cannot know for itself. The app supplies it.
 *
 * Keeping it an interface is what lets this module be tested without an app, and what keeps
 * Cognito out of it entirely — nothing here knows how a token is obtained, only that asking for one
 * may take a moment and may come back empty.
 */
interface ApiAdapter {

    val baseUrl: String

    /** Whatever is cached. Null when signed out, which is not an error: some endpoints are public. */
    suspend fun accessToken(): String?

    /**
     * A token obtained by forcing a refresh, for a request the server has just rejected.
     *
     * Distinct from [accessToken], which returns what is held and only refreshes near expiry. The
     * server can void a token long before then — removal from an organisation does exactly that —
     * and only insisting on a new one recovers from it.
     */
    suspend fun refreshedAccessToken(): String?
}
