package io.simplicity.training.api

import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the app does when the server says the token is no longer good.
 *
 * Every case here comes from a real failure on iOS rather than from imagining what might go wrong.
 * The server now voids tokens issued before somebody's access changed, so a 401 is no longer proof
 * that a session is over — it is usually proof that the token in hand is stale.
 */
class TokenAuthenticatorTest {

    private val refreshes = AtomicInteger()

    private var clock = 0L

    private fun authenticator(token: String? = "fresh") = TokenAuthenticator(
        refresh = {
            refreshes.incrementAndGet()
            token
        },
        nowMillis = { clock },
    )

    private fun response(code: Int, priorCount: Int = 0): Response {
        val request = Request.Builder().url("https://api.example.com/api/v1/me").build()
        var built = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .build()
        repeat(priorCount) {
            built = built.newBuilder()
                .priorResponse(built)
                .build()
        }
        return built
    }

    @Test
    fun `a 401 is retried once with a fresh token`() = runTest {
        val retried = authenticator().authenticate(null, response(401))

        assertNotNull(retried)
        assertEquals("Bearer fresh", retried?.header("Authorization"))
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `a second 401 gives up, so a dead session ends rather than loops`() = runTest {
        val retried = authenticator().authenticate(null, response(401, priorCount = 1))

        assertNull(retried)
    }

    @Test
    fun `a refresh that yields nothing gives up rather than retrying without a token`() = runTest {
        val retried = authenticator(token = null).authenticate(null, response(401))

        assertNull(retried)
    }

    /**
     * A 403 is authorisation, not authentication. A new token says nothing new about it, and
     * retrying would turn one refused request into two.
     */
    /**
     * The throttle, which every other test here sidesteps by using a fresh authenticator.
     *
     * Without it a persistently rejected token would spin: 401, refresh, 401, refresh. With too
     * long a window the app cannot recover from a later revocation, which is why this is two
     * seconds and not a latch.
     */
    @Test
    fun `a second refresh inside the window is refused, and allowed once past it`() = runTest {
        val authenticator = authenticator()

        assertNotNull(authenticator.authenticate(null, response(401)))
        assertEquals(1, refreshes.get())

        clock += 1_500
        assertNull(
            "a refresh 1.5s later is inside the window",
            authenticator.authenticate(null, response(401)),
        )
        assertEquals(1, refreshes.get())

        clock += 1_000
        assertNotNull(
            "2.5s after the first, the window has passed",
            authenticator.authenticate(null, response(401)),
        )
        assertEquals(2, refreshes.get())
    }

    @Test
    fun `a 403 is not retried`() = runTest {
        val retried = authenticator().authenticate(null, response(403))

        assertNull(retried)
        assertEquals(0, refreshes.get())
    }
}
