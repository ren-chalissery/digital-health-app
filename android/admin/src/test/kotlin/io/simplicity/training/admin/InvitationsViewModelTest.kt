package io.simplicity.training.admin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.CreateInvitationRequest
import io.simplicity.training.api.models.InvitationResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class InvitationsViewModelTest {

    private val organisations = mockk<OrganisationService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()

    private fun sut() = InvitationsViewModel(organisations, orgId)

    private fun invitation() = InvitationResponse(email = "new@example.org", id = UUID.randomUUID())

    @Test
    fun `a plausible address can be invited`() = runTest {
        val model = sut()
        model.emailChanged("new@example.org")

        assertTrue(model.state.value.canInvite)
    }

    @Test
    fun `an address with no at-sign cannot be invited`() = runTest {
        val model = sut()
        model.emailChanged("not-an-address")

        assertFalse(model.state.value.canInvite)

        model.invite()

        coVerify(exactly = 0) { organisations.invite(any(), any(), any(), any()) }
    }

    @Test
    fun `an address that is only an at-sign cannot be invited`() = runTest {
        val model = sut()
        model.emailChanged("@")

        assertFalse(model.state.value.canInvite)
    }

    @Test
    fun `a successful invitation is listed and the address cleared`() = runTest {
        coEvery { organisations.invite(any(), any(), any(), any()) } returns invitation()
        val model = sut()
        model.emailChanged("new@example.org")

        model.invite()

        assertEquals(1, model.state.value.invitations.size)
        assertTrue(model.state.value.email.isEmpty())
    }

    /** So it does not have to be retyped. */
    @Test
    fun `a failed invitation keeps the address`() = runTest {
        coEvery { organisations.invite(any(), any(), any(), any()) } throws IllegalStateException("nope")
        val model = sut()
        model.emailChanged("new@example.org")

        model.invite()

        assertTrue(model.state.value.failed)
        assertEquals("new@example.org", model.state.value.email)
    }

    @Test
    fun `inviting into a team sends the team as well`() = runTest {
        val teamId = UUID.randomUUID()
        coEvery { organisations.invite(any(), any(), any(), any()) } returns invitation()
        val model = sut()
        model.emailChanged("new@example.org")
        model.teamChanged(teamId)

        model.invite()

        coVerify { organisations.invite(orgId, "new@example.org", any(), teamId) }
    }

    @Test
    fun `the chosen organisation role is sent`() = runTest {
        coEvery { organisations.invite(any(), any(), any(), any()) } returns invitation()
        val model = sut()
        model.emailChanged("new@example.org")
        model.roleChanged(CreateInvitationRequest.OrgRole.ORG_ADMIN)

        model.invite()

        coVerify {
            organisations.invite(orgId, any(), CreateInvitationRequest.OrgRole.ORG_ADMIN, null)
        }
    }

    @Test
    fun `surrounding whitespace is trimmed rather than sent`() = runTest {
        coEvery { organisations.invite(any(), any(), any(), any()) } returns invitation()
        val model = sut()
        model.emailChanged("  new@example.org  ")

        model.invite()

        coVerify { organisations.invite(orgId, "new@example.org", any(), any()) }
    }
}
