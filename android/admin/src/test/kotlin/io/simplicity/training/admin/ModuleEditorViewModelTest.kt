package io.simplicity.training.admin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.AuthoredModuleResponse
import io.simplicity.training.api.models.SectionResponse
import io.simplicity.training.api.models.VersionResponse
import io.simplicity.training.services.AuthoringService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ModuleEditorViewModelTest {

    private val authoring = mockk<AuthoringService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()
    private val moduleId: UUID = UUID.randomUUID()

    private fun version(vararg titles: String) = VersionResponse(
        sections = titles.mapIndexed { index, title ->
            SectionResponse(body = "Body", position = index + 1, sectionId = UUID.randomUUID(), title = title)
        },
        versionId = UUID.randomUUID(),
    )

    private fun module(draft: VersionResponse? = null, published: VersionResponse? = null) =
        AuthoredModuleResponse(draft = draft, moduleId = moduleId, published = published, title = "A module")

    private suspend fun loaded(module: AuthoredModuleResponse): ModuleEditorViewModel {
        coEvery { authoring.module(orgId, moduleId) } returns module
        return ModuleEditorViewModel(authoring, orgId, moduleId).also { it.load() }
    }

    @Test
    fun `a module with only a published version has no draft to edit`() = runTest {
        val model = loaded(module(published = version("Intro")))

        assertFalse(model.state.value.hasDraft)
        assertTrue(model.state.value.isPublished)
    }

    /** Opening a draft copies the published version rather than modifying it. */
    @Test
    fun `opening a draft leaves the published version alone`() = runTest {
        val model = loaded(module(published = version("Intro")))
        coEvery { authoring.openDraft(orgId, moduleId) } returns
            module(draft = version("Intro"), published = version("Intro"))

        model.openDraft()

        assertTrue(model.state.value.hasDraft)
        assertTrue("the published version survives", model.state.value.isPublished)
    }

    @Test
    fun `the editor loads the draft's sections, not the published ones`() = runTest {
        val model = loaded(module(draft = version("Draft section"), published = version("Old section")))

        assertEquals(1, model.state.value.sections.size)
        assertEquals("Draft section", model.state.value.sections.first().title)
    }

    @Test
    fun `adding a section marks the editor unsaved`() = runTest {
        val model = loaded(module(draft = version("Intro")))

        model.addSection("New", "Body")

        assertTrue(model.state.value.hasUnsavedChanges)
        assertEquals(2, model.state.value.sections.size)
    }

    @Test
    fun `saving clears the unsaved marker`() = runTest {
        val model = loaded(module(draft = version("Intro")))
        coEvery { authoring.replaceSections(any(), any(), any()) } returns module(draft = version("Intro", "New"))
        model.addSection("New", "Body")

        model.saveSections()

        assertFalse(model.state.value.hasUnsavedChanges)
    }

    @Test
    fun `publishing is refused without a draft`() = runTest {
        val model = loaded(module(published = version("Intro")))

        model.publish(supersedeCompletions = false)

        coVerify(exactly = 0) { authoring.publish(any(), any(), any()) }
    }

    @Test
    fun `publishing is refused with an empty draft`() = runTest {
        val model = loaded(module(draft = VersionResponse(sections = emptyList())))

        assertFalse(model.state.value.canPublish)

        model.publish(supersedeCompletions = false)

        coVerify(exactly = 0) { authoring.publish(any(), any(), any()) }
    }

    /**
     * Superseding sends a clinician who already finished back around. Only the author can judge
     * whether a change is substantive enough to warrant that, so it is passed through untouched.
     */
    @Test
    fun `superseding completions is passed through as the author chose`() = runTest {
        val model = loaded(module(draft = version("Intro")))
        coEvery { authoring.publish(any(), any(), any()) } returns module(published = version("Intro"))

        model.publish(supersedeCompletions = true)

        coVerify(exactly = 1) { authoring.publish(orgId, moduleId, true) }
    }

    @Test
    fun `a failed publish is reported`() = runTest {
        val model = loaded(module(draft = version("Intro")))
        coEvery { authoring.publish(any(), any(), any()) } throws IllegalStateException("offline")

        model.publish(supersedeCompletions = false)

        assertTrue(model.state.value.failed)
    }
}
