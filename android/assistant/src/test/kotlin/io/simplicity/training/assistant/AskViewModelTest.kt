package io.simplicity.training.assistant

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.AnswerResponse
import io.simplicity.training.api.models.CitationResponse
import io.simplicity.training.services.AssistantService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AskViewModelTest {

    private val assistant = mockk<AssistantService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()

    private fun sut() = AskViewModel(assistant, orgId)

    @Test
    fun `an answer carries its citations`() = runTest {
        coEvery { assistant.ask(any(), any()) } returns AnswerResponse(
            answer = "The training says to check for understanding.",
            answered = true,
            citations = listOf(CitationResponse(moduleTitle = "Safety", sectionTitle = "Checking")),
        )
        val model = sut()
        model.questionChanged("How do I check understanding?")

        model.ask()

        assertEquals(1, model.state.value.citations.size)
        assertFalse(model.state.value.wasRefused)
    }

    /**
     * The behaviour most easily got wrong. A refusal is the assistant working correctly, and
     * presenting it as an error would push somebody to guess instead of asking a supervisor.
     */
    @Test
    fun `a refusal is an answer, not a failure`() = runTest {
        coEvery { assistant.ask(any(), any()) } returns AnswerResponse(
            answer = "The training does not cover that. Please raise it in supervision.",
            answered = false,
            citations = emptyList(),
        )
        val model = sut()
        model.questionChanged("What dose should I prescribe?")

        model.ask()

        assertTrue(model.state.value.wasRefused)
        assertFalse("a refusal is not an error", model.state.value.failed)
    }

    @Test
    fun `a refusal carries no citations`() = runTest {
        coEvery { assistant.ask(any(), any()) } returns
            AnswerResponse(answered = false, citations = emptyList())
        val model = sut()
        model.questionChanged("Anything")

        model.ask()

        assertTrue(model.state.value.citations.isEmpty())
    }

    @Test
    fun `an empty question is not sent`() = runTest {
        val model = sut()
        model.questionChanged("   ")

        model.ask()

        coVerify(exactly = 0) { assistant.ask(any(), any()) }
    }

    @Test
    fun `a failed request is reported`() = runTest {
        coEvery { assistant.ask(any(), any()) } throws IllegalStateException("offline")
        val model = sut()
        model.questionChanged("How do I check understanding?")

        model.ask()

        assertTrue(model.state.value.failed)
    }

    @Test
    fun `asking again clears the previous answer first`() = runTest {
        coEvery { assistant.ask(any(), any()) } returns AnswerResponse(answered = true, answer = "One")
        val model = sut()
        model.questionChanged("First")
        model.ask()

        model.reset()

        assertEquals(null, model.state.value.answer)
        assertEquals("", model.state.value.question)
    }
}
