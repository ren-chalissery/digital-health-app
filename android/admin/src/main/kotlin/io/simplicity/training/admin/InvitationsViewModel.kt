package io.simplicity.training.admin

import androidx.lifecycle.ViewModel
import io.simplicity.training.api.models.CreateInvitationRequest
import io.simplicity.training.api.models.InvitationResponse
import io.simplicity.training.services.OrganisationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class InvitationsState(
    val invitations: List<InvitationResponse> = emptyList(),
    val email: String = "",
    val role: CreateInvitationRequest.OrgRole = CreateInvitationRequest.OrgRole.ORG_MEMBER,
    val teamId: UUID? = null,
    val isBusy: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * A minimal check rather than a full address grammar. An invitation to a malformed address
     * fails silently from the sender's point of view — they see it sent and nobody arrives — so
     * catching the obvious mistakes is worth it, and rejecting valid oddities is not.
     */
    val canInvite: Boolean
        get() = email.trim().let { it.contains("@") && !it.startsWith("@") && !it.endsWith("@") } && !isBusy
}

class InvitationsViewModel(
    private val organisations: OrganisationService,
    private val orgId: UUID,
) : ViewModel() {

    private val _state = MutableStateFlow(InvitationsState())
    val state: StateFlow<InvitationsState> = _state.asStateFlow()

    fun emailChanged(value: String) = _state.update { it.copy(email = value) }
    fun roleChanged(value: CreateInvitationRequest.OrgRole) = _state.update { it.copy(role = value) }
    fun teamChanged(value: UUID?) = _state.update { it.copy(teamId = value) }

    suspend fun load() {
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            _state.update { it.copy(invitations = organisations.invitations(orgId), isBusy = false) }
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    suspend fun invite() {
        val current = _state.value
        if (!current.canInvite) return

        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            val created = organisations.invite(orgId, current.email.trim(), current.role, current.teamId)
            _state.update {
                it.copy(
                    invitations = it.invitations + created,
                    email = "",
                    teamId = null,
                    isBusy = false,
                )
            }
        } catch (e: Exception) {
            // The address stays in the field so it does not have to be retyped.
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }

    suspend fun revoke(invitation: InvitationResponse) {
        val id = invitation.id ?: return
        _state.update { it.copy(isBusy = true, failed = false) }
        try {
            organisations.revokeInvitation(orgId, id)
            _state.update { it.copy(isBusy = false) }
            load()
        } catch (e: Exception) {
            _state.update { it.copy(isBusy = false, failed = true) }
        }
    }
}
