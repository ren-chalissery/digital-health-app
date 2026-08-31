package io.simplicity.training.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the access token when the request is made rather than when the client was built.
 *
 * That distinction matters: a header captured once goes stale, because a fifteen-minute access
 * token is refreshed several times in a session.
 *
 * A missing token is not an error. The invitation preview endpoint is reached the same way and is
 * public, so the request goes out unauthenticated rather than failing here.
 */
class BearerInterceptor(private val token: suspend () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { token() }
            ?: return chain.proceed(chain.request())

        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}
