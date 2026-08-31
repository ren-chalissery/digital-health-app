package io.simplicity.training.api

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Driven through a real OkHttp client against a real socket, rather than a hand-rolled
 * `Interceptor.Chain`. The interface has a dozen members that exist to be implemented by OkHttp,
 * and standing them up by hand tests the stub rather than the interceptor.
 */
class BearerInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun headerSent(token: String?): String? {
        server.enqueue(MockResponse(code = 200))
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerInterceptor { token })
            .build()

        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

        return server.takeRequest().headers["Authorization"]
    }

    @Test
    fun `attaches a bearer header when there is a token`() {
        assertEquals("Bearer abc123", headerSent("abc123"))
    }

    /** The invitation preview is public and is reached through the same client. */
    @Test
    fun `sends no authorization header when signed out`() {
        assertNull(headerSent(null))
    }
}
