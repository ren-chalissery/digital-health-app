package io.simplicity.training.services

import io.simplicity.training.api.apis.LearningApi
import kotlinx.coroutines.test.runTest
import io.simplicity.training.api.ApiJson
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID

/**
 * Driven through the real generated Retrofit client against a real socket.
 *
 * Mocking the generated interface would test the mock. What is worth asserting is that the
 * committed client and the committed OpenAPI document agree about the wire — that a response the
 * server would really send deserialises, and that a non-2xx becomes a typed failure rather than a
 * null body surfacing as a crash somewhere else.
 */
class LearningServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: LearningService

    private val orgId: UUID = UUID.randomUUID()
    private val moduleId: UUID = UUID.randomUUID()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ApiJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LearningApi::class.java)

        service = LearningServiceImpl(api)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(body)
                .setHeader("Content-Type", "application/json")
                .build(),
        )
    }

    @Test
    fun `assigned modules deserialise from what the server sends`() = runTest {
        respond(
            """
            [{"moduleId":"$moduleId","title":"Delivering Simplicity safely",
              "status":"NOT_STARTED","sectionCount":3,"sectionsRead":0}]
            """.trimIndent(),
        )

        val modules = service.assignedModules(orgId)

        assertEquals(1, modules.size)
        assertEquals("Delivering Simplicity safely", modules.first().title)
    }

    @Test
    fun `an outstanding module is anything not completed`() = runTest {
        respond(
            """
            [{"moduleId":"$moduleId","title":"A","status":"NEEDS_REDOING","sectionCount":1,"sectionsRead":1},
             {"moduleId":"$moduleId","title":"B","status":"COMPLETED","sectionCount":1,"sectionsRead":1}]
            """.trimIndent(),
        )

        val modules = service.assignedModules(orgId)

        assertTrue("a module that came back around is still outstanding", modules[0].isOutstanding)
        assertEquals(false, modules[1].isOutstanding)
    }

    /**
     * The reason the services exist. Left to the generated client this is a null body surfacing as
     * a crash somewhere unrelated.
     */
    @Test
    fun `a 403 becomes a typed failure carrying the status`() = runTest {
        respond("""{"message":"not your organisation"}""", code = 403)

        val failure = assertThrows(ApiFailure::class.java) {
            kotlinx.coroutines.runBlocking { service.assignedModules(orgId) }
        }

        assertEquals(403, failure.status)
    }

    @Test
    fun `playback carries the caption url the video player needs`() = runTest {
        val assetId = UUID.randomUUID()
        respond("""{"url":"https://cdn.example.com/v.mp4","captionUrl":"https://cdn.example.com/c.vtt"}""")

        val playback = service.playback(orgId, assetId)

        assertEquals("https://cdn.example.com/c.vtt", playback.captionUrl)
    }
}
