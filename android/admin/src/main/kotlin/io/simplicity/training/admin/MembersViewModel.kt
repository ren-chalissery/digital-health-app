package io.simplicity.training.admin

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.ChangeOrgRoleRequest
import io.simplicity.training.api.models.OrgMemberResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class MembersState(
    val members: List<OrgMemberResponse> = emptyList(),
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    val administrators: List<OrgMemberResponse>
        get() = members.filter { it.orgRole == OrgMemberResponse.OrgRole.ORG_ADMIN }

    /**
     * Removing the last administrator would leave an organisation nobody can administer, and the
     * server refuses it. Mirrored here so the action is disabled rather than offered and rejected.
     */
    fun canRemove(member: OrgMemberResponse): Boolean {
        val isOnlyAdministrator =
            member.orgRole == OrgMemberResponse.OrgRole.ORG_ADMIN && administrators.size == 1
        return !isOnlyAdministrator
    }

    /** Demoting the only administrator has the same problem as removing them. */
    fun canDemote(member: OrgMemberResponse): Boolean = canRemove(member)
}

class MembersViewModel(
    private val organisations: OrganisationService,
    private val orgId: UUID,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersState())
    val state: StateFlow<MembersState> = _state.asStateFlow()

    suspend fun load() {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            _state.update { it.copy(members = organisations.members(orgId), isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    suspend fun changeRole(member: OrgMemberResponse, role: ChangeOrgRoleRequest.OrgRole) {
        val userId = member.userId ?: return
        if (role == ChangeOrgRoleRequest.OrgRole.ORG_MEMBER && !_state.value.canDemote(member)) return

        act { organisations.changeRole(orgId, userId, role) }
    }

    suspend fun remove(member: OrgMemberResponse) {
        val userId = member.userId ?: return
        if (!_state.value.canRemove(member)) return

        act { organisations.removeMember(orgId, userId) }
    }

    private suspend fun act(block: suspend () -> Unit) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            block()
            _state.update { it.copy(isBusy = false) }
            load()
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
