package io.simplicity.training.reflect

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.ReflectionResponse
import io.simplicity.training.services.ReflectionService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReflectViewModelTest {

    private val reflections = mockk<ReflectionService>(relaxed = true)
    private val id: UUID = UUID.randomUUID()

    private fun entry(body: String = "A reflection") =
        ReflectionResponse(body = body, id = id, title = "A title")

    private fun sut() = ReflectViewModel(reflections)

    @Test
    fun `the journal loads`() = runTest {
        coEvery { reflections.list(any()) } returns listOf(entry())
        val model = sut()

        model.load()

        assertEquals(1, model.state.value.entries.size)
    }

    @Test
    fun `saving is refused while the body is empty`() = runTest {
        val model = sut()
        model.titleChanged("A title with no body")

        assertFalse(model.state.value.canSave)

        model.save()

        coVerify(exactly = 0) { reflections.write(any(), any()) }
    }

    @Test
    fun `a new entry is written and the list refreshed`() = runTest {
        coEvery { reflections.list(any()) } returns emptyList()
        val model = sut()
        model.bodyChanged("I noticed I was rushing.")

        model.save()

        coVerify(exactly = 1) { reflections.write(null, "I noticed I was rushing.") }
        coVerify(atLeast = 1) { reflections.list(any()) }
    }

    @Test
    fun `editing an existing entry edits rather than writing a second`() = runTest {
        coEvery { reflections.list(any()) } returns emptyList()
        val model = sut()
        model.edit(entry())
        model.bodyChanged("Revised thinking.")

        model.save()

        coVerify(exactly = 1) { reflections.edit(id, "A title", "Revised thinking.") }
        coVerify(exactly = 0) { reflections.write(any(), any()) }
    }

    /** Losing a reflection to a failed save would be the worst thing this screen could do. */
    @Test
    fun `a failed save keeps the writing on screen`() = runTest {
        coEvery { reflections.write(any(), any()) } throws IllegalStateException("offline")
        val model = sut()
        model.bodyChanged("Something worth keeping.")

        model.save()

        assertTrue(model.state.value.failed)
        assertEquals("Something worth keeping.", model.state.value.body)
    }

    @Test
    fun `warnings appear as they type, without saving`() = runTest {
        val model = sut()

        model.bodyChanged("Spoke with ABC1234 today")

        assertTrue(model.state.value.warnings.any { it.kind == "an NHI number" })
        coVerify(exactly = 0) { reflections.write(any(), any()) }
    }

    /** Warnings never block. Somebody who cannot save writes it somewhere worse. */
    @Test
    fun `an entry with a warning can still be saved`() = runTest {
        coEvery { reflections.list(any()) } returns emptyList()
        val model = sut()
        model.bodyChanged("Spoke with ABC1234 today")

        assertTrue(model.state.value.canSave)

        model.save()

        coVerify(exactly = 1) { reflections.write(any(), any()) }
    }

    @Test
    fun `searching passes the query through`() = runTest {
        coEvery { reflections.list(any()) } returns emptyList()
        val model = sut()
        model.queryChanged("rushing")

        model.search()

        coVerify { reflections.list("rushing") }
    }

    @Test
    fun `cancelling editing clears the form`() = runTest {
        val model = sut()
        model.edit(entry())

        model.cancelEditing()

        assertFalse(model.state.value.isEditing)
        assertEquals("", model.state.value.body)
    }
}
