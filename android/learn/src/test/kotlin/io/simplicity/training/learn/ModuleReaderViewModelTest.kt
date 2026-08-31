package io.simplicity.training.learn

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.LearnerModuleResponse
import io.simplicity.training.api.models.SectionResponse
import io.simplicity.training.services.LearningService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ModuleReaderViewModelTest {

    private val learning = mockk<LearningService>()
    private val orgId: UUID = UUID.randomUUID()
    private val moduleId: UUID = UUID.randomUUID()

    private val first: UUID = UUID.randomUUID()
    private val second: UUID = UUID.randomUUID()

    private fun section(id: UUID, position: Int) = SectionResponse(
        body = "Body $position",
        mediaAssetId = null,
        position = position,
        sectionId = id,
        title = "Section $position",
    )

    private fun module(
        sections: List<SectionResponse>,
        read: List<UUID> = emptyList(),
        hasQuiz: Boolean = true,
    ) = LearnerModuleResponse(
        moduleId = moduleId,
        title = "Delivering Simplicity safely",
        sections = sections,
        completedSectionIds = read,
        hasQuiz = hasQuiz,
    )

    private fun sut() = ModuleReaderViewModel(learning, orgId, moduleId)

    @Test
    fun `the module loads`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns module(listOf(section(first, 1)))
        val model = sut()

        model.load()

        assertEquals("Delivering Simplicity safely", model.state.value.module?.title)
    }

    @Test
    fun `the quiz is locked while a section is unread`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns
            module(listOf(section(first, 1), section(second, 2)), read = listOf(first))
        val model = sut()

        model.load()

        assertFalse(model.state.value.allSectionsRead)
        assertFalse("offering a quiz the server would refuse is worse than not offering it", model.state.value.canTakeQuiz)
    }

    @Test
    fun `the quiz unlocks once every section is read`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns
            module(listOf(section(first, 1), section(second, 2)), read = listOf(first, second))
        val model = sut()

        model.load()

        assertTrue(model.state.value.canTakeQuiz)
    }

    /**
     * `all {}` is vacuously true on an empty list. Without the emptiness check a module with no
     * sections would report everything read and unlock a quiz nobody has studied for.
     */
    @Test
    fun `a module with no sections does not count as fully read`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns module(emptyList())
        val model = sut()

        model.load()

        assertFalse(model.state.value.allSectionsRead)
        assertFalse(model.state.value.canTakeQuiz)
    }

    @Test
    fun `a module without a quiz never offers one, however much is read`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns
            module(listOf(section(first, 1)), read = listOf(first), hasQuiz = false)
        val model = sut()

        model.load()

        assertTrue(model.state.value.allSectionsRead)
        assertFalse(model.state.value.canTakeQuiz)
    }

    @Test
    fun `resuming points at the first unread section`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns
            module(listOf(section(first, 1), section(second, 2)), read = listOf(first))
        val model = sut()

        model.load()

        assertEquals(second, model.state.value.firstUnreadSectionId)
    }

    @Test
    fun `nothing is left to resume once everything is read`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns
            module(listOf(section(first, 1)), read = listOf(first))
        val model = sut()

        model.load()

        assertNull(model.state.value.firstUnreadSectionId)
    }

    /** Progress is the server's answer, not something recomputed here. */
    @Test
    fun `marking a section read replaces the module with what the server returns`() = runTest {
        coEvery { learning.module(orgId, moduleId) } returns module(listOf(section(first, 1)))
        coEvery { learning.completeSection(orgId, first) } returns
            module(listOf(section(first, 1)), read = listOf(first))
        val model = sut()
        model.load()

        model.markRead(first)

        assertTrue(model.state.value.allSectionsRead)
        coVerify(exactly = 1) { learning.completeSection(orgId, first) }
    }

    @Test
    fun `a failed load is reported rather than left blank`() = runTest {
        coEvery { learning.module(orgId, moduleId) } throws IllegalStateException("offline")
        val model = sut()

        model.load()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isBusy)
    }
}
