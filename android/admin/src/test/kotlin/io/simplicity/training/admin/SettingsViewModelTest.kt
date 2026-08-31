package io.simplicity.training.admin

import io.mockk.coEvery
import io.mockk.mockk
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The leave warning, which iOS got wrong for a fortnight.
 *
 * It told a sole administrator that leaving "would strand this organisation" and to promote
 * somebody first — from an error branch that could never fire. The server archives the
 * organisation, deliberately. Android says so before they commit.
 */
class SettingsViewModelTest {

    private val organisations = mockk<OrganisationService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()
    private val me: UUID = UUID.randomUUID()
    private val colleague: UUID = UUID.randomUUID()

    private fun member(id: UUID, role: OrgMemberResponse.OrgRole) =
        OrgMemberResponse(email = "a@b.test", orgRole = role, userId = id)

    private fun sut(isAdmin: Boolean = true, userId: UUID? = me) =
        SettingsViewModel(organisations, orgId, userId, isAdmin)

    @Test
    fun `the only administrator is warned the organisation will be archived`() = runTest {
        coEvery { organisations.members(orgId) } returns listOf(
            member(me, OrgMemberResponse.OrgRole.ORG_ADMIN),
            member(colleague, OrgMemberResponse.OrgRole.ORG_MEMBER),
        )
        val model = sut()

        model.load()

        assertTrue(model.state.value.willArchiveOnLeave)
    }

    @Test
    fun `an administrator with a colleague to hand over to is not warned`() = runTest {
        coEvery { organisations.members(orgId) } returns listOf(
            member(me, OrgMemberResponse.OrgRole.ORG_ADMIN),
            member(colleague, OrgMemberResponse.OrgRole.ORG_ADMIN),
        )
        val model = sut()

        model.load()

        assertFalse(model.state.value.willArchiveOnLeave)
    }

    /** Only an administrator may read the member list, so an ordinary member never asks for it. */
    @Test
    fun `an ordinary member is never warned and the list is never fetched`() = runTest {
        val model = sut(isAdmin = false)

        model.load()

        assertFalse(model.state.value.willArchiveOnLeave)
        io.mockk.coVerify(exactly = 0) { organisations.members(any()) }
    }

    /** Wrongly claiming nothing will happen is milder than wrongly claiming everything will. */
    @Test
    fun `a member list that cannot be read leaves the ordinary warning in place`() = runTest {
        coEvery { organisations.members(orgId) } throws IllegalStateException("offline")
        val model = sut()

        model.load()

        assertFalse(model.state.value.willArchiveOnLeave)
        assertFalse("this is a detail of a warning, not something they asked for", model.state.value.failed)
    }

    @Test
    fun `leaving reports success so the shell can move on`() = runTest {
        coEvery { organisations.members(orgId) } returns listOf(member(me, OrgMemberResponse.OrgRole.ORG_ADMIN))
        val model = sut()
        model.load()

        model.leave()

        assertTrue(model.state.value.didLeave)
    }

    @Test
    fun `a failed leave is reported and does not claim success`() = runTest {
        coEvery { organisations.members(orgId) } returns listOf(member(me, OrgMemberResponse.OrgRole.ORG_ADMIN))
        coEvery { organisations.leave(orgId) } throws IllegalStateException("offline")
        val model = sut()
        model.load()

        model.leave()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.didLeave)
    }
}
