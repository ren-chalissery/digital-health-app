package io.simplicity.training.admin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.simplicity.training.api.models.ChangeOrgRoleRequest
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MembersViewModelTest {

    private val organisations = mockk<OrganisationService>(relaxed = true)
    private val orgId: UUID = UUID.randomUUID()
    private val adminId: UUID = UUID.randomUUID()
    private val memberId: UUID = UUID.randomUUID()

    private fun member(id: UUID, role: OrgMemberResponse.OrgRole) = OrgMemberResponse(
        email = "person@example.org",
        fullName = "A Person",
        orgRole = role,
        userId = id,
    )

    private suspend fun loaded(vararg members: OrgMemberResponse): MembersViewModel {
        coEvery { organisations.members(orgId) } returns members.toList()
        return MembersViewModel(organisations, orgId).also { it.load() }
    }

    @Test
    fun `members load`() = runTest {
        val model = loaded(member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN))

        assertTrue(model.state.value.members.isNotEmpty())
    }

    /**
     * The server refuses this, so offering it would produce a button that fails. The failure mode
     * it prevents is worse than a disabled control: an organisation nobody can administer.
     */
    @Test
    fun `the only administrator cannot be removed`() = runTest {
        val onlyAdmin = member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN)
        val model = loaded(onlyAdmin, member(memberId, OrgMemberResponse.OrgRole.ORG_MEMBER))

        assertFalse(model.state.value.canRemove(onlyAdmin))

        model.remove(onlyAdmin)

        coVerify(exactly = 0) { organisations.removeMember(any(), any()) }
    }

    @Test
    fun `the only administrator cannot be demoted either`() = runTest {
        val onlyAdmin = member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN)
        val model = loaded(onlyAdmin)

        model.changeRole(onlyAdmin, ChangeOrgRoleRequest.OrgRole.ORG_MEMBER)

        coVerify(exactly = 0) { organisations.changeRole(any(), any(), any()) }
    }

    @Test
    fun `one of two administrators can be removed`() = runTest {
        val first = member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN)
        val second = member(memberId, OrgMemberResponse.OrgRole.ORG_ADMIN)
        val model = loaded(first, second)

        assertTrue(model.state.value.canRemove(first))

        model.remove(first)

        coVerify(exactly = 1) { organisations.removeMember(orgId, adminId) }
    }

    @Test
    fun `an ordinary member can always be removed`() = runTest {
        val ordinary = member(memberId, OrgMemberResponse.OrgRole.ORG_MEMBER)
        val model = loaded(member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN), ordinary)

        model.remove(ordinary)

        coVerify(exactly = 1) { organisations.removeMember(orgId, memberId) }
    }

    @Test
    fun `promoting a member is always allowed`() = runTest {
        val ordinary = member(memberId, OrgMemberResponse.OrgRole.ORG_MEMBER)
        val model = loaded(member(adminId, OrgMemberResponse.OrgRole.ORG_ADMIN), ordinary)

        model.changeRole(ordinary, ChangeOrgRoleRequest.OrgRole.ORG_ADMIN)

        coVerify(exactly = 1) {
            organisations.changeRole(orgId, memberId, ChangeOrgRoleRequest.OrgRole.ORG_ADMIN)
        }
    }

    @Test
    fun `a failed load is reported`() = runTest {
        coEvery { organisations.members(orgId) } throws IllegalStateException("offline")
        val model = MembersViewModel(organisations, orgId)

        model.load()

        assertTrue(model.state.value.failed)
    }
}
