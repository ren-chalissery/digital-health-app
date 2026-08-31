package io.simplicity.training.api

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicLong

/**
 * Recovers from a 401 by forcing a new token and trying once more.
 *
 * OkHttp calls this precisely on a 401, and whatever it returns is the request that gets retried —
 * which makes it the right seam. Returning null gives up and lets the failure surface.
 *
 * The reason this exists at all: the server voids tokens issued before a membership change, so the
 * first 401 after somebody is removed from one of their organisations is not a dead session, it is
 * a stale token. Without a retry, a clinician who belongs to a second organisation would fail every
 * request until the old token expired — up to fifteen minutes over a change that should not have
 * touched them.
 */
class TokenAuthenticator(
    private val refresh: suspend () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : Authenticator {

    private val lastRefresh = AtomicLong(Long.MIN_VALUE)

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != HTTP_UNAUTHORIZED) {
            return null
        }
        if (response.priorResponse != null) {
            // Already retried once. A second 401 means the session really is over, and retrying
            // again would loop.
            return null
        }
        if (!mayRefresh()) {
            return null
        }

        // OkHttp's Authenticator is synchronous and is already off the main thread.
        val token = runBlocking { refresh() } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    /**
     * At most one refresh every [REFRESH_INTERVAL_MILLIS].
     *
     * Two seconds, and the number is measured rather than chosen. A JWT `iat` is whole seconds, so
     * the server cannot order a token minted in the same second as the revocation against it and
     * fails closed. A refresh landing inside that second is therefore refused too, and only the
     * next one succeeds — so the window has to clear a second boundary while keeping the person
     * waiting as briefly as possible. Every refresh also costs a round trip to Cognito, so this is
     * nowhere near a hot loop.
     */
    private fun mayRefresh(): Boolean {
        val now = nowMillis()
        val previous = lastRefresh.get()
        if (previous != Long.MIN_VALUE && now - previous < REFRESH_INTERVAL_MILLIS) {
            return false
        }
        return lastRefresh.compareAndSet(previous, now)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val REFRESH_INTERVAL_MILLIS = 2_000L
    }
}
