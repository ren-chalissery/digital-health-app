package io.simplicity.training.learn

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.AttemptResultResponse
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.MarkedQuestion
import io.simplicity.training.api.models.QuizOptionResponse
import io.simplicity.training.api.models.QuizQuestionResponse
import io.simplicity.training.api.models.QuizResponse
import io.simplicity.training.services.LearningService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QuizViewModelTest {

    private val learning = mockk<LearningService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()
    private val moduleId: UUID = UUID.randomUUID()

    private val q1: UUID = UUID.randomUUID()
    private val q2: UUID = UUID.randomUUID()
    private val optionA: UUID = UUID.randomUUID()
    private val optionB: UUID = UUID.randomUUID()

    private fun question(id: UUID, position: Int) = QuizQuestionResponse(
        options = listOf(
            QuizOptionResponse(label = "A", optionId = optionA, position = 1),
            QuizOptionResponse(label = "B", optionId = optionB, position = 2),
        ),
        position = position,
        prompt = "Question $position",
        questionId = id,
    )

    private fun sut(onChanged: (LearnerModuleResponse) -> Unit = {}) =
        QuizViewModel(learning, orgId, moduleId, onChanged)

    private suspend fun loaded(vararg questions: QuizQuestionResponse): QuizViewModel {
        coEvery { learning.quiz(orgId, moduleId) } returns QuizResponse(questions = questions.toList())
        return sut().also { it.load() }
    }

    @Test
    fun `the quiz loads`() = runTest {
        val model = loaded(question(q1, 1))

        assertEquals(1, model.state.value.questions.size)
    }

    @Test
    fun `submitting is refused until every question is answered`() = runTest {
        val model = loaded(question(q1, 1), question(q2, 2))
        model.choose(q1, optionA)

        assertFalse(model.state.value.allAnswered)

        model.submit()

        coVerify(exactly = 0) { learning.submitAttempt(any(), any(), any()) }
    }

    @Test
    fun `every question answered allows submission`() = runTest {
        val model = loaded(question(q1, 1), question(q2, 2))
        model.choose(q1, optionA)
        model.choose(q2, optionB)

        assertTrue(model.state.value.allAnswered)
    }

    /** Same vacuous-truth guard as the reader: an empty quiz is not a passed one. */
    @Test
    fun `a quiz with no questions is not answerable`() = runTest {
        val model = loaded()

        assertFalse(model.state.value.allAnswered)
    }

    @Test
    fun `changing an answer clears the previous result`() = runTest {
        val model = loaded(question(q1, 1))
        coEvery { learning.submitAttempt(any(), any(), any()) } returns
            AttemptResultResponse(passed = false, correctCount = 0, questionCount = 1)
        model.choose(q1, optionA)
        model.submit()
        assertTrue(model.state.value.result != null)

        model.choose(q1, optionB)

        assertNull("stale feedback beside a new answer would mislead", model.state.value.result)
    }

    @Test
    fun `feedback is matched to its question`() = runTest {
        val model = loaded(question(q1, 1))
        coEvery { learning.submitAttempt(any(), any(), any()) } returns AttemptResultResponse(
            passed = false,
            questions = listOf(MarkedQuestion(questionId = q1, wasCorrect = false)),
        )
        model.choose(q1, optionA)

        model.submit()

        assertEquals(false, model.state.value.feedback(q1)?.wasCorrect)
    }

    /** Completion is the server's answer. The module is re-read rather than inferred from a score. */
    @Test
    fun `passing re-reads the module rather than assuming completion`() = runTest {
        var changed: LearnerModuleResponse? = null
        coEvery { learning.quiz(orgId, moduleId) } returns QuizResponse(questions = listOf(question(q1, 1)))
        coEvery { learning.submitAttempt(any(), any(), any()) } returns
            AttemptResultResponse(passed = true, correctCount = 1, questionCount = 1)
        coEvery { learning.module(orgId, moduleId) } returns
            LearnerModuleResponse(moduleId = moduleId, title = "Done")

        val model = sut { changed = it }
        model.load()
        model.choose(q1, optionA)
        model.submit()

        assertEquals("Done", changed?.title)
    }

    @Test
    fun `failing does not re-read the module`() = runTest {
        val model = loaded(question(q1, 1))
        coEvery { learning.submitAttempt(any(), any(), any()) } returns
            AttemptResultResponse(passed = false)
        model.choose(q1, optionA)

        model.submit()

        coVerify(exactly = 0) { learning.module(any(), any()) }
    }

    @Test
    fun `a failed submission is reported`() = runTest {
        val model = loaded(question(q1, 1))
        coEvery { learning.submitAttempt(any(), any(), any()) } throws IllegalStateException("offline")
        model.choose(q1, optionA)

        model.submit()

        assertTrue(model.state.value.failed)
    }
}
